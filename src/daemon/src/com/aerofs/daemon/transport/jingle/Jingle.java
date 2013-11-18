/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.BaseParam.XMPP;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.TransportThreadGroup;
import com.aerofs.daemon.transport.lib.DevicePresenceListener;
import com.aerofs.daemon.transport.lib.IUnicastCallbacks;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.PresenceService;
import com.aerofs.daemon.transport.lib.PulseManager;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.Unicast;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler.ChannelMode;
import com.aerofs.daemon.transport.lib.handlers.ClientHandler;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService;
import com.aerofs.daemon.transport.xmpp.multicast.Multicast;
import com.aerofs.daemon.transport.xmpp.presence.XMPPPresenceProcessor;
import com.aerofs.j.Jid;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.proto.Diagnostics.JingleDevice;
import com.aerofs.proto.Diagnostics.JingleDiagnostics;
import com.aerofs.proto.Diagnostics.PBDumpStat;
import com.aerofs.proto.Diagnostics.PBDumpStat.PBTransport;
import com.aerofs.proto.Diagnostics.ServerStatus;
import com.aerofs.proto.Ritual.GetTransportDiagnosticsReply;
import com.aerofs.rocklog.RockLog;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Set;

import static com.aerofs.daemon.transport.lib.TPUtil.setupCommonHandlersAndListeners;
import static com.aerofs.daemon.transport.lib.TPUtil.setupMulticastHandler;
import static com.aerofs.daemon.transport.lib.TransportUtil.fromInetSockAddress;
import static com.aerofs.daemon.transport.lib.TransportUtil.getReachabilityErrorString;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

public class Jingle implements ITransport, IUnicastCallbacks
{
    private static final String SIGNAL_THREAD_THREAD_ID = "st";

    private final BlockingPrioQueue<IEvent> _q;
    private final EventDispatcher _disp = new EventDispatcher();
    private final Scheduler _sched;
    private final IBlockingPrioritizedEventSink<IEvent> _sink;
    private final TransportStats _transportStats;
    private final PresenceService _presenceService;
    private final PulseManager _pulseManager = new PulseManager();
    private final StreamManager _streamManager = new StreamManager();
    private final DID _localDID;
    private final Jid _localJid;
    private final String _id;
    private final int _rank;
    private final SignalThread _signalThread;
    private final Multicast _multicast;
    private final XMPPConnectionService _xmppConnectionService;
    private final String _xmppServerDomain;
    private final Unicast _unicast;

    public Jingle(
            UserID localUser,
            DID localdid,
            InetSocketAddress stunServerAddress,
            InetSocketAddress xmppServerAddress,
            String xmppServerDomain,
            long xmppServerConnectionLinkStateChangePingInterval,
            int numPingsBeforeDisconnectingXmppServerConnection,
            long xmppServerConnectionInitialReconnectInterval,
            long xmppServerConnectionMaxReconnectInterval,
            byte[] scrypted,
            String absRtRoot,
            boolean enableJingleLibraryLogging,
            String id,
            int rank,
            IBlockingPrioritizedEventSink<IEvent> sink,
            LinkStateService linkStateService,
            MaxcastFilterReceiver maxcastFilterReceiver,
            RockLog rockLog,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory)
    {
        OSUtil.get().loadLibrary("aerofsj");

        _localDID = localdid;
        _localJid = JingleUtils.did2jid(_localDID, xmppServerDomain);
        _id = id;
        _rank = rank;
        _q = new BlockingPrioQueue<IEvent>(DaemonParam.XMPP.QUEUE_LENGTH);
        _sink = sink;
        _sched = new Scheduler(_q, id + "-sched");
        _transportStats = new TransportStats();

        _xmppServerDomain = xmppServerDomain;
        _xmppConnectionService = new XMPPConnectionService(
                id,
                localdid,
                xmppServerAddress,
                xmppServerDomain,
                id,
                scrypted,
                xmppServerConnectionLinkStateChangePingInterval,
                numPingsBeforeDisconnectingXmppServerConnection,
                xmppServerConnectionInitialReconnectInterval,
                xmppServerConnectionMaxReconnectInterval,
                rockLog,
                linkStateService);

        // Signal thread
        _signalThread = new SignalThread(_localJid, _xmppConnectionService.getXmppPassword(), stunServerAddress, xmppServerAddress, absRtRoot, enableJingleLibraryLogging);
        _signalThread.setDaemon(true);
        _signalThread.setName(SIGNAL_THREAD_THREAD_ID);

        // Presence Service
        _presenceService = new PresenceService();

        // Unicast
        _unicast = new Unicast(this, _transportStats);
        linkStateService.addListener(_unicast, sameThreadExecutor()); // can be notified on any thread since Unicast is thread-safe

        ChannelTeardownHandler serverChannelTeardownHandler = new ChannelTeardownHandler(this, _sink, _streamManager, ChannelMode.SERVER);
        ChannelTeardownHandler clientChannelTeardownHandler = new ChannelTeardownHandler(this, _sink, _streamManager, ChannelMode.CLIENT);
        TransportProtocolHandler protocolHandler = new TransportProtocolHandler(this, _sink, _streamManager, _pulseManager, _unicast);
        JingleBootstrapFactory bsFact = new JingleBootstrapFactory(_id, localUser, localdid, clientSslEngineFactory, serverSslEngineFactory, _presenceService, rockLog, _transportStats);
        ServerBootstrap serverBootstrap = bsFact.newServerBootstrap(_signalThread, _unicast, protocolHandler, serverChannelTeardownHandler);
        ClientBootstrap clientBootstrap = bsFact.newClientBootstrap(_signalThread, clientChannelTeardownHandler);
        _unicast.setBootstraps(serverBootstrap, clientBootstrap);

        // Multicast XMPP Message Presence Processor
        XMPPPresenceProcessor xmppPresenceProcessor = new XMPPPresenceProcessor(localdid, this, _sink, _presenceService); // notify presence service whenever device comes online/offline on multicast

        // Presence Service Wiring
        _signalThread.setListener(_presenceService); // presence service is notified whenever signal thread goes up/down
        _unicast.setUnicastListener(_presenceService); // presence service is notified whenever a device connects/disconnects to unicast
        _presenceService.addListener(new DevicePresenceListener(_id, _unicast, _pulseManager, rockLog)); // shut down pulsing and disconnect everyone when they go offline
        _presenceService.addListener(xmppPresenceProcessor); // send any final offline presence to the core when people go offline

        // Multicast
        _multicast = new Multicast(localdid, id, xmppServerDomain, maxcastFilterReceiver, _xmppConnectionService, this, _sink);
        setupMulticastHandler(_disp, _multicast);

        // Warning: it is very important that XMPPPresenceProcessor listens to the XMPPConnectionService _before_
        // Multicast. The reason is that Multicast will join the chat rooms and this will trigger
        // sending the presence information. So if we add Multicast as a listener first, the presence
        // information will already be sent by the time the presence manager registers to get them.
        _xmppConnectionService.addListener(xmppPresenceProcessor);
        _xmppConnectionService.addListener(_multicast);
    }

    @Override
    public void init_()
            throws Exception
    {
        setupCommonHandlersAndListeners(this, _disp, _sched, _sink, _multicast, _streamManager, _pulseManager, _unicast, _presenceService);
    }

    @Override
    public void start_()
    {
        // Event loop
        new Thread(TransportThreadGroup.get(), new Runnable() {
            @Override
            public void run()
            {
                OutArg<Prio> outPrio = new OutArg<Prio>();
                //noinspection InfiniteLoopStatement
                while (true) {
                    IEvent ev = _q.dequeue(outPrio);
                    _disp.dispatch_(ev, outPrio.get());
                }
            }
        }, id() + "-eq").start();

        _signalThread.start();

        _unicast.start(new JingleAddress(_localDID, _localJid));

        _xmppConnectionService.start();
    }

    @Override
    public boolean supportsMulticast()
    {
        return true;
    }

    @Override
    public IBlockingPrioritizedEventSink<IEvent> q()
    {
        return _q;
    }

    @Override
    public void dumpStat(PBDumpStat dstemplate, final PBDumpStat.Builder dsbuilder)
            throws Exception
    {
        // TODO (GS): Copied from TCP

        PBTransport tp = checkNotNull(dstemplate.getTransport(0));
        PBTransport.Builder tpbuilder = PBTransport.newBuilder();
        if (tp.hasName()) tpbuilder.setName(id());

        dsbuilder.addTransport(tpbuilder);

        try {
            _unicast.dumpStat(dstemplate, dsbuilder);
        } catch (Exception e) {
            // FIXME: put in a message saying there was an error
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
            throws Exception
    {
        String indent2 = indent + indentUnit;

        ps.println(indent + "q");
        _q.dumpStatMisc(indent2, indentUnit, ps);

        ps.println(indent + "xmpp");
        _xmppConnectionService.dumpStatMisc(indent2, indentUnit, ps);
    }

    @Override
    public String id()
    {
        return _id;
    }

    @Override
    public int rank()
    {
        return _rank;
    }

    @Override
    public long bytesIn()
    {
        return _transportStats.getBytesReceived();
    }

    @Override
    public long bytesOut()
    {
        return _transportStats.getBytesSent();
    }

    @Override
    public SocketAddress resolve(DID did)
            throws ExDeviceOffline
    {
        return new JingleAddress(did, JingleUtils.did2jid(did, _xmppServerDomain));
    }

    @Override
    public void dumpDiagnostics(GetTransportDiagnosticsReply.Builder transportDiagnostics)
    {
        transportDiagnostics.setJingleDiagnostics(getDiagnostics());
    }

    private JingleDiagnostics getDiagnostics()
    {
        JingleDiagnostics.Builder diagnostics = JingleDiagnostics.newBuilder();

        // xmpp

        ServerStatus.Builder xmppServerStatus = ServerStatus
                .newBuilder()
                .setServerAddress(fromInetSockAddress(XMPP.SERVER_ADDRESS));

        try {
            xmppServerStatus.setReachable(_xmppConnectionService.isReachable());
        } catch (IOException e) {
            xmppServerStatus.setReachable(false);
            xmppServerStatus.setReachabilityError(getReachabilityErrorString(xmppServerStatus, e));
        }

        diagnostics.setXmppServer(xmppServerStatus);

        // stun

        ServerStatus.Builder stunServerStatus = ServerStatus
                .newBuilder()
                .setServerAddress(fromInetSockAddress(DaemonParam.Jingle.STUN_SERVER_ADDRESS));

        // FIXME (AG): actually check STUN reachability
        //
        // we can't determine reachability without including a full-out STUN client
        // the best I can do is check if the address can be resolved. if it can't
        // we know for sure the server is unreachable

        if (!stunServerStatus.getServerAddress().hasResolvedHost()) {
            stunServerStatus.setReachable(false);
            stunServerStatus.setReachabilityError(getReachabilityErrorString(stunServerStatus, new UnknownHostException()));
        }

        diagnostics.setStunServer(stunServerStatus);

        // presence

        Set<DID> available = _presenceService.allPotentiallyAvailable();
        for (DID did : available) {
            diagnostics.addReachableDevices(JingleDevice.newBuilder().setDid(did.toPB()));
        }

        return diagnostics.build();
    }

    @Override
    public void onClientCreated(ClientHandler client)
    {
        // nothing to do
    }
}
