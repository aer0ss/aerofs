/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.BaseParam.XMPP;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.TransportThreadGroup;
import com.aerofs.daemon.transport.lib.ILinkStateListener;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.PulseManager;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.daemon.transport.lib.TransportProtocolHandler;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.netty.ClientHandler;
import com.aerofs.daemon.transport.netty.IUnicastCallbacks;
import com.aerofs.daemon.transport.netty.Unicast;
import com.aerofs.daemon.transport.xmpp.Multicast;
import com.aerofs.daemon.transport.xmpp.PresenceStore;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService;
import com.aerofs.daemon.transport.xmpp.XMPPPresenceManager;
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
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Set;

import static com.aerofs.daemon.transport.lib.TPUtil.registerCommonHandlers;
import static com.aerofs.daemon.transport.lib.TPUtil.registerMulticastHandler;
import static com.aerofs.daemon.transport.lib.TransportUtil.fromInetSockAddress;
import static com.aerofs.daemon.transport.lib.TransportUtil.getReachabilityErrorString;
import static com.google.common.base.Preconditions.checkNotNull;

public class Jingle implements ITransport, ILinkStateListener, IUnicastCallbacks
{
    private static final String SIGNAL_THREAD_THREAD_ID = "st";

    private final BlockingPrioQueue<IEvent> _q;
    private final IBlockingPrioritizedEventSink<IEvent> _sink;
    private final EventDispatcher _disp = new EventDispatcher();
    private final Scheduler _sched;
    private final TransportStats _transportStats;
    private final PresenceStore _presenceStore = new PresenceStore();
    private final PulseManager _pulseManager = new PulseManager();
    private final StreamManager _streamManager = new StreamManager();
    private final DID _localDID;
    private final Jid _localJid;
    private final String _id;
    private final int _rank;
    private final SignalThread _signalThread;
    private final Multicast _multicast;
    private final XMPPConnectionService _xmppServer;
    private final String _xmppServerDomain;
    private final Unicast _unicast;

    public Jingle(
            UserID localUser,
            DID localDID,
            InetSocketAddress stunServerAddress,
            InetSocketAddress xmppServerAddress,
            String xmppServerDomain,
            byte[] scrypted,
            String absRtRoot,
            boolean enableJingleLibraryLogging,
            String id, int rank,
            IBlockingPrioritizedEventSink<IEvent> sink,
            MaxcastFilterReceiver mcfr,
            RockLog rockLog,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory)
    {
        OSUtil.get().loadLibrary("aerofsj");

        _localDID = localDID;
        _localJid = JingleUtils.did2jid(_localDID, xmppServerDomain);
        _id = id;
        _rank = rank;
        _sink = sink;
        _q = new BlockingPrioQueue<IEvent>(DaemonParam.XMPP.QUEUE_LENGTH);
        _sched = new Scheduler(_q, id + "-sched");
        _transportStats = new TransportStats();

        _xmppServer = new XMPPConnectionService(localDID, xmppServerAddress, xmppServerDomain, id, scrypted, rockLog); // FIXME (AG): inject this
        _xmppServerDomain = xmppServerDomain;

        // Signal thread
        _signalThread = new SignalThread(_localJid, stunServerAddress, xmppServerAddress, _xmppServer, absRtRoot, enableJingleLibraryLogging);
        _signalThread.setDaemon(true);
        _signalThread.setName(SIGNAL_THREAD_THREAD_ID);

        // Unicast
        _unicast = new Unicast(this, _transportStats);
        TransportProtocolHandler protocolHandler = new TransportProtocolHandler(this, sink, _streamManager, _pulseManager, _unicast);
        JingleBootstrapFactory bsFact = new JingleBootstrapFactory(_id, localUser, localDID, clientSslEngineFactory, serverSslEngineFactory, rockLog, _transportStats);
        ServerBootstrap serverBootstrap = bsFact.newServerBootstrap(_signalThread, _unicast, protocolHandler);
        ClientBootstrap clientBootstrap = bsFact.newClientBootstrap(_signalThread);
        _unicast.setBootstraps(serverBootstrap, clientBootstrap);

        // Presence Manager
        XMPPPresenceManager presenceManager = new XMPPPresenceManager(this, localDID, sink, _presenceStore, _pulseManager, _unicast);

        // Multicast
        _multicast = new Multicast(localDID, id, xmppServerDomain, mcfr, _xmppServer, this, sink);
        registerMulticastHandler(_disp, _multicast);

        // Warning: it is very important that XMPPPresenceManager listens to the XMPPServer _before_
        // Multicast. The reason is that Multicast will join the chat rooms and this will trigger
        // sending the presence information. So if we add Multicast as a listener first, the presence
        // information will already be sent by the time the presence manager registers to get them.
        _xmppServer.addListener(presenceManager);
        _xmppServer.addListener(_multicast);
    }

    @Override
    public void init_()
            throws Exception
    {
        registerCommonHandlers(_disp, _sched, this, _multicast, _streamManager, _pulseManager, _unicast, _presenceStore);
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
    }

    @Override
    public void linkStateChanged(
            Set<NetworkInterface> removed,
            Set<NetworkInterface> added,
            Set<NetworkInterface> prev,
            Set<NetworkInterface> current)
    {
        final boolean upBefore = !prev.isEmpty();
        final boolean upNow = !current.isEmpty();

        // Going down
        if (upBefore && !upNow) {
            _unicast.pauseAccept();
            _xmppServer.linkStateChanged(false);
        }

        // Going up
        if (!upBefore && upNow) {
            _unicast.resumeAccept();
            // If the signal thread is not ready, do not start the xmpp server. The signal thread
            // will start the xmpp server when it's ready.
            if (_signalThread.isReady()) _xmppServer.linkStateChanged(true);
        }
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
        _xmppServer.dumpStatMisc(indent2, indentUnit, ps);

        ps.println(indent + "ucast");
        _unicast.dumpStatMisc(indent2, indentUnit, ps);
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
            xmppServerStatus.setReachable(_xmppServer.isReachable());
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

        Set<DID> available = _presenceStore.availablePeers();
        for (DID did : available) {
            diagnostics.addReachableDevices(JingleDevice.newBuilder().setDid(did.toPB()));
        }

        return diagnostics.build();
    }

    @Override
    public void closePeerStreams(DID did, boolean outbound, boolean inbound)
    {
        TPUtil.sessionEnded(new Endpoint(this, did), _sink, _streamManager, outbound, inbound);
    }

    @Override
    public void onClientCreated(ClientHandler client)
    {
        // nothing to do
    }
}
