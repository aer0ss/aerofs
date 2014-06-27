/**
 * Created by Weihan Wang, Air Computing Inc.
 * Authors: Weihan Wang, Allen A. George
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.link.ILinkStateListener;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.ChannelMonitor;
import com.aerofs.daemon.transport.lib.DevicePresenceListener;
import com.aerofs.daemon.transport.lib.IAddressResolver;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.PresenceService;
import com.aerofs.daemon.transport.lib.PulseManager;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TransportEventQueue;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.TransportUtil;
import com.aerofs.daemon.transport.lib.Unicast;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler.ChannelMode;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.daemon.transport.tcp.ARP.ARPEntry;
import com.aerofs.daemon.transport.tcp.ARP.IARPVisitor;
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
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.rocklog.RockLog;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.protobuf.Message;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.List;

import static com.aerofs.daemon.lib.DaemonParam.TCP.ARP_GC_INTERVAL;
import static com.aerofs.daemon.lib.DaemonParam.TCP.HEARTBEAT_INTERVAL;
import static com.aerofs.daemon.transport.lib.TransportProtocolUtil.setupCommonHandlersAndListeners;
import static com.aerofs.daemon.transport.lib.TransportProtocolUtil.setupMulticastHandler;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

// FIXME (AG): remove direct call from Stores and make this final
public class TCP implements ITransport, IAddressResolver
{
    private static final Logger l = Loggers.getLogger(TCP.class);

    private static final int PORT_ANY = 0;

    private final TransportEventQueue transportEventQueue;
    private final EventDispatcher dispatcher;
    private final Scheduler scheduler;
    private final String id;
    private final int pref;
    private final ARP arp;
    private final TransportStats transportStats;
    private final Stores stores;
    private final Unicast unicast;
    private final Multicast multicast;
    private final IBlockingPrioritizedEventSink<IEvent> outgoingEventSink;
    private final StreamManager streamManager = new StreamManager();
    private final PulseManager pulseManager = new PulseManager();
    private final PresenceService presenceService = new PresenceService();
    private final ChannelMonitor monitor;

    public TCP(
            UserID localUser,
            DID localdid,
            String id,
            int pref,
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
            RockLog rockLog,
            ClientSocketChannelFactory clientChannelFactory,
            ServerSocketChannelFactory serverChannelFactory)
    {
        this.dispatcher = new EventDispatcher();
        this.transportEventQueue = new TransportEventQueue(id, this.dispatcher);
        this.scheduler = new Scheduler(this.transportEventQueue, id + "-sch");

        this.id = id;
        this.pref = pref;
        this.transportStats = new TransportStats();
        this.outgoingEventSink = outgoingEventSink;

        this.multicast = new Multicast(localdid, this, listenToMulticastOnLoopback, maxcastFilterReceiver);
        linkStateService.addListener(multicast, sameThreadExecutor()); // can notify on the link-state thread because Multicast is thread-safe

        // unicast
        this.unicast = new Unicast(this, this);
        monitor = new ChannelMonitor(unicast.getDirectory(), timer);
        this.arp = new ARP(monitor);

        this.stores = new Stores(localdid, this, arp, multicast);
        multicast.setStores(stores);

        ChannelTeardownHandler serverChannelTeardownHandler = new ChannelTeardownHandler(this, this.outgoingEventSink, streamManager, ChannelMode.SERVER);
        ChannelTeardownHandler clientChannelTeardownHandler = new ChannelTeardownHandler(this, this.outgoingEventSink, streamManager, ChannelMode.CLIENT);
        TCPProtocolHandler tcpProtocolHandler = new TCPProtocolHandler(stores, unicast);
        TransportProtocolHandler protocolHandler = new TransportProtocolHandler(this, outgoingEventSink, streamManager, pulseManager);
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
                rockLog);
        ServerBootstrap serverBootstrap = bootstrapFactory.newServerBootstrap(serverChannelFactory,
                serverChannelTeardownHandler);
        ClientBootstrap clientBootstrap = bootstrapFactory.newClientBootstrap(clientChannelFactory, clientChannelTeardownHandler);
        unicast.setBootstraps(serverBootstrap, clientBootstrap);
        linkStateService.addListener(unicast, sameThreadExecutor());

        // presence hookups
        unicast.setUnicastListener(presenceService);
        multicast.setListener(monitor);
        presenceService.addListener(new DevicePresenceListener(id, unicast, pulseManager, rockLog));
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

                arp.visitARPEntries(new ARP.IARPVisitor()
                {
                    @Override
                    public void visit(DID did, ARPEntry arp)
                    {
                        evicted.add(did);
                    }
                });

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
        setupCommonHandlersAndListeners(this, dispatcher, scheduler, outgoingEventSink, stores, streamManager, pulseManager, unicast, presenceService);
        setupMulticastHandler(dispatcher, multicast);

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

                arp.visitARPEntries(new ARP.IARPVisitor()
                {
                    @Override
                    public void visit(DID did, ARPEntry arp)
                    {
                        if (arp.lastUpdatedTimer.elapsed() > ARP_GC_INTERVAL) {
                            evicted.add(did);
                        }
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
    public boolean supportsMulticast()
    {
        return true;
    }

    @Override
    public void start()
    {
        // Start these in the right order; bind the listener, THEN start processing the queues.
        // Otherwise, handling events could die; some handlers assume the transport was started
        unicast.start(new InetSocketAddress(PORT_ANY));
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
        return pref;
    }

    @Override
    public IBlockingPrioritizedEventSink<IEvent> q()
    {
        return transportEventQueue;
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

        arp.visitARPEntries(new IARPVisitor()
        {
            @Override
            public void visit(DID did, ARPEntry arp)
            {
                TCPDevice.Builder deviceBuilder = TCPDevice
                        .newBuilder()
                        .setDid(did.toPB())
                        .setDeviceAddress(TransportUtil.fromInetSockAddress(arp.remoteAddress, false));

                for (Message message : unicast.getChannelDiagnostics(did)) {
                    deviceBuilder.addChannel((TCPChannel) message);
                }

                diagnostics.addReachableDevices(deviceBuilder);
            }
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
}
