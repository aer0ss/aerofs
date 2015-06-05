/**
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.transport.lib.exceptions.ExTransportUnavailable;
import com.aerofs.daemon.transport.lib.*;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.aerofs.daemon.transport.presence.TCPPresenceLocation;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.link.ILinkStateListener;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.lib.exceptions.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler.ChannelMode;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.proto.Diagnostics.PBInetSocketAddress;
import com.aerofs.proto.Diagnostics.TCPChannel;
import com.aerofs.proto.Diagnostics.TCPDevice;
import com.aerofs.proto.Diagnostics.TCPDiagnostics;
import com.aerofs.proto.Diagnostics.TransportDiagnostics;
import com.aerofs.proto.Transport;
import com.aerofs.proto.Transport.PBTPHeader;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.protobuf.Message;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import static com.aerofs.daemon.lib.DaemonParam.TCP.ARP_GC_INTERVAL;
import static com.aerofs.daemon.lib.DaemonParam.TCP.HEARTBEAT_INTERVAL;
import static com.aerofs.daemon.transport.lib.TransportProtocolUtil.setupCommonHandlersAndListeners;
import static com.aerofs.proto.Transport.PBStream.Type.BEGIN_STREAM;
import static com.aerofs.proto.Transport.PBTPHeader.Type.STREAM;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

// FIXME (AG): remove direct call from TCPStores and make this final
public class TCP implements ITransport, IAddressResolver
{
    private static final Logger l = Loggers.getLogger(TCP.class);

    private final TransportEventQueue transportEventQueue;
    private final EventDispatcher dispatcher;
    private final Scheduler scheduler;
    private final String id;
    private final DID localdid;
    private final int rank;
    private final ARP arp;
    private final TransportStats transportStats;
    public final TCPStores stores;
    private final Unicast unicast;
    public final Multicast multicast;
    private final IBlockingPrioritizedEventSink<IEvent> outgoingEventSink;
    private final StreamManager streamManager;
    private final PresenceService presenceService = new PresenceService();
    public final ChannelMonitor monitor;
    private final LinkStateService linkStateService;
    private final PortRange portRange;

    public TCP(
            UserID localUser,
            DID localdid,
            long streamTimeout,
            String id,
            int rank,
            IBlockingPrioritizedEventSink<IEvent> outgoingEventSink,
            LinkStateService linkStateService,
            boolean listenToMulticastOnLoopback,
            long channelConnectTimeout,
            long heartbeatInterval,
            int maxFailedHeartbeats,
            MaxcastFilterReceiver maxcastFilterReceiver,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory,
            Timer timer,
            ClientSocketChannelFactory clientChannelFactory,
            ServerSocketChannelFactory serverChannelFactory,
            IRoundTripTimes roundTripTimes)
    {
        this.dispatcher = new EventDispatcher();
        this.transportEventQueue = new TransportEventQueue(id, this.dispatcher);
        this.scheduler = new Scheduler(this.transportEventQueue, id + "-sch");

        this.id = id;
        this.rank = rank;
        this.transportStats = new TransportStats();
        this.outgoingEventSink = outgoingEventSink;
        this.streamManager = new StreamManager(streamTimeout);
        this.localdid = localdid;
        this.portRange = PortRange.loadFromConfiguration();

        this.multicast = new Multicast(localdid, this, listenToMulticastOnLoopback, maxcastFilterReceiver);

        // unicast
        this.unicast = new Unicast(this, this);
        monitor = new ChannelMonitor(unicast.getDirectory(), timer);
        this.arp = new ARP(monitor);

        this.stores = new TCPStores(localdid, this, arp, multicast, presenceService);
        multicast.setStores(stores);

        ChannelTeardownHandler serverChannelTeardownHandler = new ChannelTeardownHandler(this, streamManager, ChannelMode.SERVER);
        ChannelTeardownHandler clientChannelTeardownHandler = new ChannelTeardownHandler(this, streamManager, ChannelMode.CLIENT);
        TCPProtocolHandler tcpProtocolHandler = new TCPProtocolHandler(stores, unicast);
        TransportProtocolHandler protocolHandler = new TransportProtocolHandler(this, outgoingEventSink, streamManager);

        TCPBootstrapFactory bootstrapFactory = new TCPBootstrapFactory(
                localUser,
                localdid,
                channelConnectTimeout,
                heartbeatInterval,
                maxFailedHeartbeats,
                clientSslEngineFactory,
                serverSslEngineFactory,
                presenceService,
                unicast,
                protocolHandler,
                tcpProtocolHandler,
                transportStats,
                timer,
                roundTripTimes);
        ServerBootstrap serverBootstrap = bootstrapFactory.newServerBootstrap(serverChannelFactory,
                serverChannelTeardownHandler);
        ClientBootstrap clientBootstrap = bootstrapFactory.newClientBootstrap(clientChannelFactory, clientChannelTeardownHandler);
        unicast.setBootstraps(serverBootstrap, clientBootstrap);

        // For TCP only, the link state listeners have a strange (and bad) interdependency.
        // The XMPPMulticast class controls whether we are sending/receiving tcp ping/pong traffic, and
        // winds up talking to ARP.
        // The Unicast infrastructure does bad things if it is asked to deal with a peer before the
        // link state (suspended/resumed) flag has been updated internally.
        //
        //  Since the Notifier as we use it does not provide a way to guarantee safe ordering, we
        // do so explicitly with the following trivial compound notifier.
        //
        linkStateService.addListener((previous, current, added, removed) -> {
            unicast.onLinkStateChanged(previous, current, added, removed);
            multicast.onLinkStateChanged(previous, current, added, removed);
        }, sameThreadExecutor());
        this.linkStateService = linkStateService;

        // presence hookups
        unicast.setUnicastStateListener(presenceService);
        unicast.setDeviceConnectionListener(presenceService);
        multicast.setListener(monitor);
        presenceService.addListener(stores);
        presenceService.addListener(monitor);

        // flush all the arp entries immediately when the link-state changes
        linkStateService.addListener(new ILinkStateListener()
        {
            @Override
            public void onLinkStateChanged(
                    ImmutableSet<NetworkInterface> previous,
                    ImmutableSet<NetworkInterface> current,
                    ImmutableSet<NetworkInterface> added,
                    ImmutableSet<NetworkInterface> removed)
            {

                if (!previous.isEmpty() && current.isEmpty()) {
                    evictAllArpEntries();
                }
            }

            private void evictAllArpEntries()
            {
                final List<DID> evicted = Lists.newArrayList();

                arp.visitARPEntries((did, arp1) -> evicted.add(did));

                // Call remove() out of the visitor to avoid holding the ARP lock
                for (DID did : evicted) {
                    arp.remove(did);
                }

            }
        }, sameThreadExecutor());
    }

    @Override
    public void init() throws Exception
    {
        // must be called *after* the Unicast object is initialized.
        setupCommonHandlersAndListeners(dispatcher, streamManager, unicast);

        multicast.init();

        scheduler.schedule(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                arpGC();

                scheduler.schedule(this, ARP_GC_INTERVAL);
            }

            private void arpGC()
            {
                final List<DID> evicted = Lists.newArrayList();

                arp.visitARPEntries((did, arp1) -> {
                    if (arp1.lastUpdatedTimer.elapsed() > ARP_GC_INTERVAL) {
                        evicted.add(did);
                    }
                });

                // Call remove() out of the visitor to avoid holding the ARP lock
                for (DID did : evicted) {
                    arp.remove(did);
                }
            }

        }, ARP_GC_INTERVAL);

        scheduler.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                try {
                    l.trace("arp sender: sched pong");

                    PBTPHeader pong = stores.newPongMessage(true);
                    if (pong != null) {
                        multicast.sendControlMessage(pong);
                    }
                } catch (Exception e) {
                    l.warn("fail mc pong", LogUtil.suppress(e, IOException.class));
                }

                scheduler.schedule(this, HEARTBEAT_INTERVAL);
            }
        }, HEARTBEAT_INTERVAL);
    }

    @Override
    public void start()
    {
        // Start these in the right order; bind the listener, THEN start processing the queues.
        // Otherwise, handling events could die; some handlers assume the transport was started
        while (portRange.hasNext()) {
            try {
                unicast.start(new InetSocketAddress(portRange.next()));
                break;
            } catch (ChannelException e) {
                if (portRange.hasNext()) {
                    l.info("Failed to bind to local port, retrying on next port", e);
                } else {
                    throw e;
                }
            }
        }
        transportEventQueue.start();

        l.info("listening to {}", getListeningPort());
    }

    @Override
    public void stop()
    {
        // multicast.stop(); FIXME (AG): have a stop method!
        unicast.stop();
        scheduler.shutdown();
        transportEventQueue.stop();
    }

    @Override
    public String id()
    {
        return id;
    }

    @Override
    public String toString()
    {
        return id();
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
    public OutgoingStream newOutgoingStream(DID did)
            throws ExDeviceUnavailable, ExTransportUnavailable
    {
        StreamKey sk = streamManager.newOutgoingStreamKey(did);

        PBTPHeader h = PBTPHeader.newBuilder()
                .setType(STREAM)
                .setStream(Transport.PBStream
                        .newBuilder()
                        .setType(BEGIN_STREAM)
                        .setStreamId(sk.strmid.getInt()))
                .build();

        // NB. we will not catch failures of sending ctrl msg. however it
        // will be reflected when sending the payload data below
        Channel channel = (Channel)unicast.send(did, TransportProtocolUtil.newControl(h), null);
        return streamManager.newOutgoingStream(sk, channel);
    }

    IBlockingPrioritizedEventSink<IEvent> sink()
    {
        return outgoingEventSink;
    }

    /**
     * @return the port that the tcp server is listening to
     */
    int getListeningPort()
    {
        return ((InetSocketAddress)unicast.getListeningAddress()).getPort();
    }

    /**
     * Collect and return the list of presence locations
     *
     * @return list of PresenceLocations for this transport
     */
    @Override
    public ArrayList<IPresenceLocation> getPresenceLocations()
    {
        ArrayList<IPresenceLocation> locations = new ArrayList<>();

        // We have the listening port
        int listeningPort = getListeningPort();

        // We need the list of IPs from the linkStateService
        for (InetAddress addr: linkStateService.getCurrentIPs()) {
            locations.add(new TCPPresenceLocation(localdid, addr, listeningPort));
        }

        return locations;
    }

    @Override
    public SocketAddress resolve(DID did)
            throws ExDeviceUnavailable
    {
        return arp.getThrows(did).remoteAddress;
    }

    @Override
    public void dumpDiagnostics(TransportDiagnostics.Builder transportDiagnostics)
    {
        transportDiagnostics.setTcpDiagnostics(getDiagnostics());
    }

    private TCPDiagnostics getDiagnostics()
    {
        final TCPDiagnostics.Builder diagnostics = TCPDiagnostics.newBuilder();

        // listening port

        int listeningPort = ((InetSocketAddress) unicast.getListeningAddress()).getPort();
        PBInetSocketAddress.Builder ourAddress = PBInetSocketAddress.newBuilder();
        if (listeningPort > 0) {
            ourAddress.setHost("*").setPort(listeningPort);
        }
        diagnostics.setListeningAddress(ourAddress);

        // reachable_devices

        arp.visitARPEntries((did, arp1) -> {
            TCPDevice.Builder deviceBuilder = TCPDevice
                    .newBuilder()
                    .setDid(BaseUtil.toPB(did))
                    .setDeviceAddress(TransportUtil.fromInetSockAddress(arp1.remoteAddress));

            for (Message message : unicast.getChannelDiagnostics(did)) {
                deviceBuilder.addChannel((TCPChannel) message);
            }

            diagnostics.addReachableDevices(deviceBuilder);
        });

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

    private static class PortRange
    {
        final int low, high;
        private int offset;

        PortRange(int low, int high)
        {
            this.low = low;
            this.high = high;
            this.offset = 0;
        }

        boolean hasNext()
        {
            return low + offset <= high;
        }

        int next()
        {
            return this.low + (offset++);
        }

        static PortRange loadFromConfiguration()
        {
            int low = LibParam.Daemon.PORT_RANGE_LOW, high = LibParam.Daemon.PORT_RANGE_HIGH;
            if (low > high) {
                l.warn("invalid range of ports for daemon to use: {}-{}, defaulting to any port", low, high);
                return new PortRange(0, 0);
            } else if (high > 0 && low <= 1024) {
                l.warn("invalid range of ports for daemon to use: {}-{} includes reserved ports, defaulting to any port", low, high);
                return new PortRange(0, 0);
            }
            return new PortRange(Math.max(0, low), Math.min(65535, high));
        }
    }
}
