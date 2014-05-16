/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.BaseParam.XMPP;
import com.aerofs.base.TimerUtil;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.ChannelPreallocator;
import com.aerofs.daemon.transport.lib.DevicePresenceListener;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.PresenceService;
import com.aerofs.daemon.transport.lib.PulseManager;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TransportEventQueue;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.Unicast;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler.ChannelMode;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService;
import com.aerofs.daemon.transport.xmpp.multicast.Multicast;
import com.aerofs.daemon.transport.xmpp.presence.XMPPPresenceProcessor;
import com.aerofs.j.Jid;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.proto.Diagnostics.JingleChannel;
import com.aerofs.proto.Diagnostics.JingleDevice;
import com.aerofs.proto.Diagnostics.JingleDiagnostics;
import com.aerofs.proto.Diagnostics.ServerStatus;
import com.aerofs.proto.Diagnostics.TransportDiagnostics;
import com.aerofs.rocklog.RockLog;
import com.google.protobuf.Message;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.util.Timer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Set;

import static com.aerofs.daemon.transport.lib.TransportProtocolUtil.setupCommonHandlersAndListeners;
import static com.aerofs.daemon.transport.lib.TransportProtocolUtil.setupMulticastHandler;
import static com.aerofs.daemon.transport.lib.TransportUtil.fromInetSockAddress;
import static com.aerofs.daemon.transport.lib.TransportUtil.getReachabilityErrorString;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

public class Jingle implements ITransport
{
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
            long channelConnectTimeout,
            long heartbeatInterval,
            int maxFailedHeartbeats,
            byte[] scrypted,
            String absRtRoot,
            boolean enableJingleLibraryLogging,
            String id,
            int rank,
            Timer timer,
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
        this.scheduler = new Scheduler(this.transportEventQueue, id + "-sch");

        this.localdid = localdid;
        this.localjid = JingleUtils.did2jid(this.localdid, xmppServerDomain);
        this.id = id;
        this.rank = rank;
        this.outgoingEventSink = outgoingEventSink;
        this.transportStats = new TransportStats();

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
        JingleAddressResolver resolver = new JingleAddressResolver(xmppServerDomain);
        this.unicast = new Unicast(resolver, this);
        linkStateService.addListener(unicast, sameThreadExecutor()); // can be notified on any thread since Unicast is thread-safe

        ChannelTeardownHandler serverChannelTeardownHandler = new ChannelTeardownHandler(this, this.outgoingEventSink, streamManager, ChannelMode.SERVER);
        ChannelTeardownHandler clientChannelTeardownHandler = new ChannelTeardownHandler(this, this.outgoingEventSink, streamManager, ChannelMode.CLIENT);
        TransportProtocolHandler protocolHandler = new TransportProtocolHandler(this, this.outgoingEventSink, streamManager, pulseManager);
        // FIXME (AG): if I can somehow remove the circular dependency for IServerHandlerListener I can completely remove the setBootstraps call!
        JingleBootstrapFactory bootstrapFactory = new JingleBootstrapFactory(
                localUser,
                localdid,
                channelConnectTimeout,
                heartbeatInterval,
                maxFailedHeartbeats,
                timer,
                clientSslEngineFactory,
                serverSslEngineFactory,
                presenceService,
                rockLog,
                unicast,
                protocolHandler,
                transportStats,
                signalThread,
                channelWorker);
        ServerBootstrap serverBootstrap = bootstrapFactory.newServerBootstrap(serverChannelTeardownHandler);
        ClientBootstrap clientBootstrap = bootstrapFactory.newClientBootstrap(clientChannelTeardownHandler);
        unicast.setBootstraps(serverBootstrap, clientBootstrap);

        // process presence messages that come via XMPP
        XMPPPresenceProcessor xmppPresenceProcessor = new XMPPPresenceProcessor(localdid, xmppServerDomain, this, this.outgoingEventSink, presenceService); // notify presence service whenever device comes online/offline on multicast

        // presence service wiring
        signalThread.setUnicastListener(presenceService); // presence service is notified whenever signal thread goes up/down
        unicast.setUnicastListener(presenceService); // presence service is notified whenever a device connects/disconnects to unicast
        presenceService.addListener(new DevicePresenceListener(this.id, unicast, pulseManager, rockLog)); // shut down pulsing and disconnect everyone when they go offline
        presenceService.addListener(new ChannelPreallocator(presenceService, unicast.getDirectory(), TimerUtil.getGlobalTimer())); // try connecting when we hear of a device
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
    public void dumpDiagnostics(TransportDiagnostics.Builder transportDiagnostics)
    {
        transportDiagnostics.setJingleDiagnostics(getDiagnostics());
    }

    private JingleDiagnostics getDiagnostics()
    {
        JingleDiagnostics.Builder diagnostics = JingleDiagnostics.newBuilder();

        // xmpp

        ServerStatus.Builder xmppServerStatus = ServerStatus
                .newBuilder()
                .setServerAddress(fromInetSockAddress(XMPP.SERVER_ADDRESS, true));

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
                .setServerAddress(fromInetSockAddress(DaemonParam.Jingle.STUN_SERVER_ADDRESS, true));

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
            JingleDevice.Builder deviceBuilder = JingleDevice
                    .newBuilder()
                    .setDid(did.toPB());

            for (Message message : unicast.getChannelDiagnostics(did)) {
                deviceBuilder.addChannel((JingleChannel) message);
            }

            diagnostics.addReachableDevices(deviceBuilder);
        }

        return diagnostics.build();
    }
}
