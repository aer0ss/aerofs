/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.BaseParam.XMPP;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.DevicePresenceListener;
import com.aerofs.daemon.transport.lib.IUnicastCallbacks;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.PresenceService;
import com.aerofs.daemon.transport.lib.PulseManager;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TransportEventQueue;
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
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger l = LoggerFactory.getLogger(Jingle.class);

    private final TransportEventQueue transportEventQueue;
    private final EventDispatcher dispatcher;
    private final Scheduler scheduler;
    private final IBlockingPrioritizedEventSink<IEvent> outgoingEventSink;
    private final TransportStats transportStats;
    private final PresenceService presenceService;
    private final PulseManager pulseManager = new PulseManager();
    private final StreamManager streamManager = new StreamManager();
    private final DID localdid;
    private final Jid localjid;
    private final String id;
    private final int rank;
    private final SignalThread signalThread;
    private final JingleChannelWorker channelWorker;
    private final Multicast multicast;
    private final XMPPConnectionService xmppConnectionService;
    private final String xmppServerDomain;
    private final Unicast unicast;

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
            IBlockingPrioritizedEventSink<IEvent> outgoingEventSink,
            LinkStateService linkStateService,
            MaxcastFilterReceiver maxcastFilterReceiver,
            RockLog rockLog,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory)
    {
        OSUtil.get().loadLibrary("aerofsj");

        this.dispatcher = new EventDispatcher();
        this.transportEventQueue = new TransportEventQueue(id, this.dispatcher);
        this.scheduler = new Scheduler(this.transportEventQueue, id + "-sched");

        this.localdid = localdid;
        this.localjid = JingleUtils.did2jid(this.localdid, xmppServerDomain);
        this.id = id;
        this.rank = rank;
        this.outgoingEventSink = outgoingEventSink;
        this.transportStats = new TransportStats();

        this.xmppServerDomain = xmppServerDomain;
        this.xmppConnectionService = new XMPPConnectionService(
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

        this.signalThread = new SignalThread(id, localjid, xmppConnectionService.getXmppPassword(), stunServerAddress, xmppServerAddress, absRtRoot, enableJingleLibraryLogging);
        this.channelWorker = new JingleChannelWorker(id);
        this.presenceService = new PresenceService();

        // unicast
        this.unicast = new Unicast(this, transportStats);
        linkStateService.addListener(unicast, sameThreadExecutor()); // can be notified on any thread since Unicast is thread-safe

        ChannelTeardownHandler serverChannelTeardownHandler = new ChannelTeardownHandler(this, this.outgoingEventSink, streamManager, ChannelMode.SERVER);
        ChannelTeardownHandler clientChannelTeardownHandler = new ChannelTeardownHandler(this, this.outgoingEventSink, streamManager, ChannelMode.CLIENT);
        TransportProtocolHandler protocolHandler = new TransportProtocolHandler(this, this.outgoingEventSink, streamManager, pulseManager, unicast);
        JingleBootstrapFactory bsFact = new JingleBootstrapFactory(this.id, localUser, localdid, clientSslEngineFactory, serverSslEngineFactory, presenceService, rockLog, transportStats, channelWorker);
        ServerBootstrap serverBootstrap = bsFact.newServerBootstrap(signalThread, unicast, protocolHandler, serverChannelTeardownHandler);
        ClientBootstrap clientBootstrap = bsFact.newClientBootstrap(signalThread, clientChannelTeardownHandler);
        unicast.setBootstraps(serverBootstrap, clientBootstrap);

        // process presence messages that come via XMPP
        XMPPPresenceProcessor xmppPresenceProcessor = new XMPPPresenceProcessor(localdid, xmppServerDomain, this, this.outgoingEventSink, presenceService); // notify presence service whenever device comes online/offline on multicast

        // presence service wiring
        signalThread.setUnicastListener(presenceService); // presence service is notified whenever signal thread goes up/down
        unicast.setUnicastListener(presenceService); // presence service is notified whenever a device connects/disconnects to unicast
        presenceService.addListener(new DevicePresenceListener(this.id, unicast, pulseManager, rockLog)); // shut down pulsing and disconnect everyone when they go offline
        presenceService.addListener(xmppPresenceProcessor); // send any final offline presence to the core when people go offline

        // multicast
        this.multicast = new Multicast(localdid, id, xmppServerDomain, maxcastFilterReceiver, xmppConnectionService, this, this.outgoingEventSink);
        setupMulticastHandler(dispatcher, multicast);

        // Warning: it is very important that XMPPPresenceProcessor listens to the XMPPConnectionService _before_
        // Multicast. The reason is that Multicast will join the chat rooms and this will trigger
        // sending the presence information. So if we add Multicast as a listener first, the presence
        // information will already be sent by the time the presence manager registers to get them.
        xmppConnectionService.addListener(xmppPresenceProcessor);
        xmppConnectionService.addListener(multicast);
    }

    @Override
    public void init()
            throws Exception
    {
        setupCommonHandlersAndListeners(this, dispatcher, scheduler, outgoingEventSink, multicast, streamManager, pulseManager, unicast, presenceService);
    }

    @Override
    public void start()
    {
        channelWorker.start();
        transportEventQueue.start();
        signalThread.start();
        unicast.start(new JingleAddress(localdid, localjid));
        xmppConnectionService.start();
    }

    @Override
    public void stop()
    {
        xmppConnectionService.stop();
        unicast.stop();
        signalThread.shutdown();
        scheduler.shutdown();
        transportEventQueue.stop();
        channelWorker.stop();
    }

    @Override
    public boolean supportsMulticast()
    {
        return true;
    }

    @Override
    public IBlockingPrioritizedEventSink<IEvent> q()
    {
        return transportEventQueue;
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
            unicast.dumpStat(dstemplate, dsbuilder);
        } catch (Exception e) {
            l.warn("fail unicast dumpstat");
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
            throws Exception
    {
        String indent2 = indent + indentUnit;
        ps.println(indent + "q");
        transportEventQueue.dumpStatMisc(indent2, indentUnit, ps);
        ps.println(indent + "xmpp");
        xmppConnectionService.dumpStatMisc(indent2, indentUnit, ps);
        // TODO (AG): add dump for unicast and multicast
    }

    @Override
    public String toString()
    {
        return id();
    }

    @Override
    public String id()
    {
        return id;
    }

    @Override
    public int rank()
    {
        return rank;
    }

    @Override
    public long bytesIn()
    {
        return transportStats.getBytesReceived();
    }

    @Override
    public long bytesOut()
    {
        return transportStats.getBytesSent();
    }

    @Override
    public SocketAddress resolve(DID did)
    {
        return new JingleAddress(did, JingleUtils.did2jid(did, xmppServerDomain));
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
            xmppServerStatus.setReachable(xmppConnectionService.isReachable());
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

        Set<DID> available = presenceService.allPotentiallyAvailable();
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
