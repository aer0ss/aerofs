/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.zephyr;

import com.aerofs.base.BaseParam.XMPP;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.mobile.MobileServerZephyrConnector;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.DevicePresenceListener;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.PresenceService;
import com.aerofs.daemon.transport.lib.PulseManager;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TransportEventQueue;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.UnicastProxy;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService.IXMPPConnectionServiceListener;
import com.aerofs.daemon.transport.xmpp.multicast.Multicast;
import com.aerofs.daemon.transport.xmpp.presence.XMPPPresenceProcessor;
import com.aerofs.daemon.transport.xmpp.signalling.SignallingService;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.proto.Diagnostics.PBDumpStat;
import com.aerofs.proto.Diagnostics.ServerStatus;
import com.aerofs.proto.Diagnostics.TransportDiagnostics;
import com.aerofs.proto.Diagnostics.ZephyrDevice;
import com.aerofs.proto.Diagnostics.ZephyrDiagnostics;
import com.aerofs.rocklog.RockLog;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.XMPPConnection;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Set;

import static com.aerofs.daemon.transport.lib.TPUtil.setupCommonHandlersAndListeners;
import static com.aerofs.daemon.transport.lib.TPUtil.setupMulticastHandler;
import static com.aerofs.daemon.transport.lib.TransportUtil.fromInetSockAddress;
import static com.aerofs.daemon.transport.lib.TransportUtil.getReachabilityErrorString;
import static com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler.ChannelMode.TWOWAY;
import static com.google.common.base.Preconditions.checkState;

public final class Zephyr implements ITransport
{
    protected static final Logger l = Loggers.getLogger(Zephyr.class);

    private final String id;
    private final int rank; // FIXME (AG): why does the transport need to know its own preference

    private final TransportEventQueue transportEventQueue;
    private final EventDispatcher dispatcher;
    private final Scheduler scheduler;
    private final IBlockingPrioritizedEventSink<IEvent> outgoingEventSink;

    private final PulseManager pulseManager = new PulseManager();
    private final StreamManager streamManager = new StreamManager();

    private final PresenceService presenceService; // FIXME (AG): remove dependency of pulsing system on the presence system
    private final Multicast multicast;
    private final XMPPConnectionService xmppConnectionService;

    private final InetSocketAddress zephyrAddress;
    private final ZephyrConnectionService zephyrConnectionService;

    private final TransportStats transportStats = new TransportStats();

    private boolean multicastEnabled = false;

    public Zephyr(
            UserID localid,
            DID localdid,
            byte[] scrypted,
            String id,
            int rank,
            IBlockingPrioritizedEventSink<IEvent> outgoingEventSink,
            LinkStateService linkStateService,
            MaxcastFilterReceiver maxcastFilterReceiver,
            SSLEngineFactory clientSSLEngineFactory,
            SSLEngineFactory serverSSLEngineFactory,
            ClientSocketChannelFactory clientSocketChannelFactory,
            final @Nullable MobileServerZephyrConnector mobileServerZephyrConnector,
            RockLog rockLog,
            InetSocketAddress xmppServerAddress,
            String xmppServerDomain,
            long xmppServerConnectionLinkStateChangePingInterval,
            int numPingsBeforeDisconnectingXmppServerConnection,
            long xmppServerConnectionInitialReconnectInterval,
            long xmppServerConnectionMaxReconnectInterval,
            InetSocketAddress zephyrAddress,
            Proxy proxy)
    {
        checkState(DaemonParam.XMPP.CONNECT_TIMEOUT > DaemonParam.Zephyr.HANDSHAKE_TIMEOUT); // should be much larger!

        // this is a unicastProxy for NullPointerException during authentication
        // see http://www.igniterealtime.org/community/thread/35976
        SASLAuthentication.supportSASLMechanism("PLAIN", 0);

        this.dispatcher = new EventDispatcher();
        this.transportEventQueue = new TransportEventQueue(id, this.dispatcher);
        this.scheduler = new Scheduler(this.transportEventQueue, id + "-sched");

        this.id = id;
        this.rank = rank;

        this.outgoingEventSink = outgoingEventSink;

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

        this.multicast = new Multicast(localdid, id, xmppServerDomain, maxcastFilterReceiver, xmppConnectionService, this, outgoingEventSink);
        this.presenceService = new PresenceService();

        XMPPPresenceProcessor xmppPresenceProcessor = new XMPPPresenceProcessor(localdid, xmppServerDomain, this, outgoingEventSink, presenceService);
        presenceService.addListener(xmppPresenceProcessor);

        UnicastProxy unicastProxy = new UnicastProxy();
        SignallingService signallingService = new SignallingService(id, xmppServerDomain, xmppConnectionService);
        TransportProtocolHandler transportProtocolHandler = new TransportProtocolHandler(this, outgoingEventSink, streamManager, pulseManager, unicastProxy);
        ChannelTeardownHandler channelTeardownHandler = new ChannelTeardownHandler(this, outgoingEventSink, streamManager, TWOWAY);
        this.zephyrAddress = zephyrAddress;
        this.zephyrConnectionService = new ZephyrConnectionService(
                localid,
                localdid,
                clientSSLEngineFactory,
                serverSSLEngineFactory,
                presenceService,
                linkStateService,
                signallingService,
                transportProtocolHandler,
                channelTeardownHandler,
                transportStats,
                rockLog,
                clientSocketChannelFactory,
                this.zephyrAddress,
                proxy);
        unicastProxy.setRealUnicast(zephyrConnectionService);

        presenceService.addListener(new DevicePresenceListener(id, zephyrConnectionService, pulseManager, rockLog));

        // WARNING: it is very important that XMPPPresenceProcessor listen to XMPPConnectionService
        // _before_ Multicast. The reason is that Multicast will join the chat rooms and this will trigger
        // sending the presence information. So if we add Multicast as a listener first, the presence
        // information will already be sent by the time the presence manager registers to get them.
        xmppConnectionService.addListener(xmppPresenceProcessor);
        xmppConnectionService.addListener(multicast);

        // FIXME (AG): This should be removed, and mobileServerZephyrConnection should do this itself
        if (mobileServerZephyrConnector != null) {
            xmppConnectionService.addListener(new IXMPPConnectionServiceListener()
            {
                @Override
                public void xmppServerConnected(XMPPConnection xmppConnection)
                        throws Exception
                {
                    mobileServerZephyrConnector.setConnection(xmppConnection);
                }

                @Override
                public void xmppServerDisconnected()
                {
                    // intentionally a noop
                    // looks like mobileServerZephyrConnector doesn't
                    // know how to deal with null connections, and I really
                    // don't want to touch that code
                }
            });
        }
    }

    public void enableMulticast()
    {
        l.debug("{}: enabling multicast", id());

        multicastEnabled = true;
        setupMulticastHandler(dispatcher, multicast);
    }

    @Override
    public boolean supportsMulticast()
    {
        return multicastEnabled;
    }

    @Override
    public void init()
            throws Exception
    {
        setupCommonHandlersAndListeners(this, dispatcher, scheduler, outgoingEventSink, multicast, streamManager, pulseManager, zephyrConnectionService, presenceService);
        zephyrConnectionService.init();
    }

    @Override
    public void start()
    {
        transportEventQueue.start();
        xmppConnectionService.start();
        zephyrConnectionService.start();
    }

    @Override
    public void stop()
    {
        zephyrConnectionService.stop();
        xmppConnectionService.stop();
        scheduler.shutdown();
        transportEventQueue.stop();
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
    public void dumpStat(PBDumpStat template, PBDumpStat.Builder bd)
    {
        zephyrConnectionService.dumpStat(template, bd);
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        String indent2 = indent + indentUnit;

        ps.println(indent + "q");
        transportEventQueue.dumpStatMisc(indent2, indentUnit, ps);
        ps.println(indent + "xmpp");
        xmppConnectionService.dumpStatMisc(indent2, indentUnit, ps);
        ps.println(indent + "unicast");
        zephyrConnectionService.dumpStatMisc(indent2, indentUnit, ps);
    }

    @Override
    public void dumpDiagnostics(TransportDiagnostics.Builder transportDiagnostics)
    {
        transportDiagnostics.setZephyrDiagnostics(getDiagnostics());
    }

    private ZephyrDiagnostics getDiagnostics()
    {
        ZephyrDiagnostics.Builder diagnostics = ZephyrDiagnostics.newBuilder();

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

        // zephyr

        ServerStatus.Builder zephyrServerStatus = ServerStatus
                .newBuilder()
                .setServerAddress(fromInetSockAddress(zephyrAddress));

        try {
            zephyrServerStatus.setReachable(zephyrConnectionService.isReachable());
        } catch (IOException e) {
            zephyrServerStatus.setReachable(false);
            zephyrServerStatus.setReachabilityError(getReachabilityErrorString(zephyrServerStatus, e));
        }

        diagnostics.setZephyrServer(zephyrServerStatus);

        // presence

        Set<DID> available = presenceService.allPotentiallyAvailable();
        for (DID did : available) {
            diagnostics.addReachableDevices(ZephyrDevice.newBuilder().setDid(did.toPB()));
        }

        return diagnostics.build();
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
}
