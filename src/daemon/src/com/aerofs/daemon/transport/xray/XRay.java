/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.xray;

import com.aerofs.base.BaseParam.XMPP;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.ChannelMonitor;
import com.aerofs.daemon.transport.lib.IRoundTripTimes;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.PresenceService;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TransportEventQueue;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService;
import com.aerofs.daemon.transport.xmpp.multicast.Multicast;
import com.aerofs.daemon.transport.xmpp.presence.XMPPPresenceProcessor;
import com.aerofs.daemon.transport.xmpp.signalling.SignallingService;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.proto.Diagnostics.ServerStatus;
import com.aerofs.proto.Diagnostics.TransportDiagnostics;
import com.aerofs.proto.Diagnostics.XRayDevice;
import com.aerofs.proto.Diagnostics.XRayDiagnostics;
import com.google.common.collect.Sets;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.util.Timer;
import org.jivesoftware.smack.SASLAuthentication;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Collection;
import java.util.Set;

import static com.aerofs.daemon.transport.lib.TransportProtocolUtil.setupCommonHandlersAndListeners;
import static com.aerofs.daemon.transport.lib.TransportProtocolUtil.setupMulticastHandler;
import static com.aerofs.daemon.transport.lib.TransportUtil.fromInetSockAddress;
import static com.aerofs.daemon.transport.lib.TransportUtil.getReachabilityErrorString;
import static com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler.ChannelMode.TWOWAY;
import static com.google.common.base.Preconditions.checkState;

// FIXME (AG): investigate using Unicast instead of hand-tuned connection service
public final class XRay implements ITransport
{
    protected static final Logger l = Loggers.getLogger(XRay.class);

    private final String id;
    private final int rank; // FIXME (AG): why does the transport need to know its own preference

    private final TransportEventQueue transportEventQueue;
    private final EventDispatcher dispatcher;
    private final Scheduler scheduler;

    private final StreamManager streamManager = new StreamManager();

    private final PresenceService presenceService;
    private final Multicast multicast;
    private final XMPPConnectionService xmppConnectionService;

    private final InetSocketAddress xrayAddress;
    private final XRayConnectionService xrayConnectionService;

    private final TransportStats transportStats = new TransportStats();
    private final ChannelMonitor monitor;

    private boolean multicastEnabled = false;

    public XRay(
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
            Timer timer,
            InetSocketAddress xmppServerAddress,
            String xmppServerDomain,
            long xmppServerConnectionLinkStateChangePingInterval,
            int numPingsBeforeDisconnectingXmppServerConnection,
            long xmppServerConnectionInitialReconnectInterval,
            long xmppServerConnectionMaxReconnectInterval,
            long heartbeatInterval,
            int maxFailedHeartbeats,
            long xrayHandshakeTimeout,
            InetSocketAddress xrayAddress,
            Proxy proxy,
            IRoundTripTimes roundTripTimes)
    {
        checkState(DaemonParam.XMPP.CONNECT_TIMEOUT > DaemonParam.XRay.HANDSHAKE_TIMEOUT); // should be much larger!

        // this is required to avoid a NullPointerException during authentication
        // see http://www.igniterealtime.org/community/thread/35976
        SASLAuthentication.supportSASLMechanism("PLAIN", 0);

        this.dispatcher = new EventDispatcher();
        this.transportEventQueue = new TransportEventQueue(id, this.dispatcher);
        this.scheduler = new Scheduler(this.transportEventQueue, id + "-sch");

        this.id = id;
        this.rank = rank;

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
                linkStateService);

        this.multicast = new Multicast(localdid, id, xmppServerDomain, maxcastFilterReceiver,
                xmppConnectionService, this, outgoingEventSink);
        this.presenceService = new PresenceService();


        SignallingService signallingService = new SignallingService(id, xmppServerDomain, xmppConnectionService);
        TransportProtocolHandler transportProtocolHandler = new TransportProtocolHandler(this, outgoingEventSink, streamManager);
        ChannelTeardownHandler channelTeardownHandler = new ChannelTeardownHandler(this, outgoingEventSink, streamManager, TWOWAY);
        this.xrayAddress = xrayAddress;
        this.xrayConnectionService = new XRayConnectionService(
                localid,
                localdid,
                heartbeatInterval,
                maxFailedHeartbeats,
                xrayHandshakeTimeout,
                clientSSLEngineFactory,
                serverSSLEngineFactory,
                this,
                presenceService,
                linkStateService,
                signallingService,
                transportProtocolHandler,
                channelTeardownHandler,
                transportStats,
                timer,
                clientSocketChannelFactory,
                this.xrayAddress,
                proxy,
                roundTripTimes);

        this.monitor = new ChannelMonitor(xrayConnectionService.getDirectory(), timer);

        XMPPPresenceProcessor xmppPresenceProcessor = new XMPPPresenceProcessor(localdid, xmppServerDomain, this, outgoingEventSink, monitor);
        presenceService.addListener(xmppPresenceProcessor);
        presenceService.addListener(monitor);

        // WARNING: it is very important that XMPPPresenceProcessor listen to XMPPConnectionService
        // _before_ Multicast. The reason is that Multicast will join the chat rooms and this will trigger
        // sending the presence information. So if we add Multicast as a listener first, the presence
        // information will already be sent by the time the presence manager registers to get them.
        xmppConnectionService.addListener(xmppPresenceProcessor);
        xmppConnectionService.addListener(multicast);
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
        setupCommonHandlersAndListeners(dispatcher, multicast, streamManager, xrayConnectionService);
        xrayConnectionService.init();
    }

    @Override
    public void start()
    {
        transportEventQueue.start();
        xmppConnectionService.start();
        xrayConnectionService.start();
    }

    @Override
    public void stop()
    {
        xrayConnectionService.stop();
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
    public void dumpDiagnostics(TransportDiagnostics.Builder transportDiagnostics)
    {
        transportDiagnostics.setXrayDiagnostics(getDiagnostics());
    }

    private XRayDiagnostics getDiagnostics()
    {
        XRayDiagnostics.Builder diagnostics = XRayDiagnostics.newBuilder();

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

        // zephyr

        ServerStatus.Builder xrayServerStatus = ServerStatus
                .newBuilder()
                .setServerAddress(fromInetSockAddress(xrayAddress, true));

        try {
            xrayServerStatus.setReachable(xrayConnectionService.isReachable());
        } catch (IOException e) {
            xrayServerStatus.setReachable(false);
            xrayServerStatus.setReachabilityError(getReachabilityErrorString(xrayServerStatus, e));
        }

        diagnostics.setXrayServer(xrayServerStatus);

        // devices

        // get all devices available to the presence service
        Set<DID> availableDevices = Sets.newHashSet(monitor.allReachableDevices());

        // get all devices for which we have connections
        Collection<XRayDevice> connectedDevices = xrayConnectionService.getDeviceDiagnostics();

        // remove all devices that we know for which we have connections
        for (XRayDevice device : connectedDevices) {
            availableDevices.remove(new DID(device.getDid()));
        }

        // add these 'empty' devices first
        for (DID did : availableDevices) {
            diagnostics.addReachableDevices(XRayDevice.newBuilder().setDid(did.toPB()));
        }

        // now add all the devices for which we have connections
        diagnostics.addAllReachableDevices(connectedDevices);

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
