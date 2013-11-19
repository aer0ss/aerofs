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
import com.aerofs.daemon.transport.lib.DevicePresenceListener;
import com.aerofs.daemon.transport.lib.IUnicastCallbacks;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.PresenceService;
import com.aerofs.daemon.transport.lib.PulseManager;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.daemon.transport.lib.TransportEventQueue;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.Unicast;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler.ChannelMode;
import com.aerofs.daemon.transport.lib.handlers.ClientHandler;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.daemon.transport.tcp.ARP.ARPEntry;
import com.aerofs.daemon.transport.tcp.ARP.IARPListener;
import com.aerofs.daemon.transport.tcp.ARP.IARPVisitor;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.proto.Diagnostics.PBDumpStat;
import com.aerofs.proto.Diagnostics.PBDumpStat.PBTransport;
import com.aerofs.proto.Diagnostics.PBInetSocketAddress;
import com.aerofs.proto.Diagnostics.TCPDevice;
import com.aerofs.proto.Diagnostics.TCPDiagnostics;
import com.aerofs.proto.Ritual.GetTransportDiagnosticsReply;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import com.aerofs.rocklog.RockLog;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.slf4j.Logger;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.List;

import static com.aerofs.daemon.lib.DaemonParam.TCP.ARP_GC_INTERVAL;
import static com.aerofs.daemon.lib.DaemonParam.TCP.HEARTBEAT_INTERVAL;
import static com.aerofs.daemon.transport.lib.TPUtil.setupCommonHandlersAndListeners;
import static com.aerofs.daemon.transport.lib.TPUtil.setupMulticastHandler;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newLinkedList;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

// FIXME (AG): remove direct call from Stores and make this final
public class TCP implements ITransport, IUnicastCallbacks
{
    private static final Logger l = Loggers.getLogger(TCP.class);

    private static final int PORT_ANY = 0;

    private final TransportEventQueue transportEventQueue;
    private final EventDispatcher dispatcher;
    private final Scheduler scheduler;
    private final DID localdid;
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

    public TCP(
            UserID localUser,
            DID localdid,
            String id,
            int pref,
            IBlockingPrioritizedEventSink<IEvent> outgoingEventSink,
            LinkStateService linkStateService,
            boolean listenToMulticastOnLoopback,
            MaxcastFilterReceiver maxcastFilterReceiver,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory,
            RockLog rockLog,
            ClientSocketChannelFactory clientChannelFactory,
            ServerSocketChannelFactory serverChannelFactory)
    {
        this.dispatcher = new EventDispatcher();
        this.transportEventQueue = new TransportEventQueue(id, this.dispatcher);
        this.scheduler = new Scheduler(this.transportEventQueue, id + "-sched");

        this.localdid = localdid;
        this.id = id;
        this.pref = pref;
        this.arp = new ARP();
        this.transportStats = new TransportStats();
        this.outgoingEventSink = outgoingEventSink;

        this.multicast = new Multicast(localdid, this, listenToMulticastOnLoopback, maxcastFilterReceiver);
        linkStateService.addListener(multicast, sameThreadExecutor()); // can notify on the link-state thread because Multicast is thread-safe

        this.stores = new Stores(this.localdid, this, arp, multicast);
        multicast.setStores(stores);

        // unicast
        this.unicast = new Unicast(this, transportStats);
        ChannelTeardownHandler serverChannelTeardownHandler = new ChannelTeardownHandler(this, this.outgoingEventSink, streamManager, ChannelMode.SERVER);
        ChannelTeardownHandler clientChannelTeardownHandler = new ChannelTeardownHandler(this, this.outgoingEventSink, streamManager, ChannelMode.CLIENT);
        TCPProtocolHandler tcpProtocolHandler = new TCPProtocolHandler(stores, unicast);
        TransportProtocolHandler protocolHandler = new TransportProtocolHandler(this, outgoingEventSink, streamManager, pulseManager, unicast);
        TCPBootstrapFactory bsFact = new TCPBootstrapFactory(this.id, localUser, localdid, clientSslEngineFactory, serverSslEngineFactory, presenceService, rockLog, transportStats);
        ServerBootstrap serverBootstrap = bsFact.newServerBootstrap(serverChannelFactory, unicast, tcpProtocolHandler, protocolHandler, serverChannelTeardownHandler);
        ClientBootstrap clientBootstrap = bsFact.newClientBootstrap(clientChannelFactory, clientChannelTeardownHandler);
        unicast.setBootstraps(serverBootstrap, clientBootstrap);
        linkStateService.addListener(unicast, sameThreadExecutor());

        // presence hookups
        unicast.setUnicastListener(presenceService);
        multicast.setListener(presenceService);
        presenceService.addListener(new DevicePresenceListener(id, unicast, pulseManager, rockLog));
        presenceService.addListener(stores);
        arp.addListener(new IARPListener()
        {
            @Override
            public void onARPEntryChange(DID did, boolean added)
            {
                if (added) {
                    presenceService.onDeviceReachable(did);
                } else {
                    presenceService.onDeviceUnreachable(did);
                }
            }
        });

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
                    l.debug("arp sender: sched pong");

                    PBTPHeader pong = stores.newPongMessage(true);
                    if (pong != null) {
                        multicast.sendControlMessage(pong);
                    }
                } catch (Exception e) {
                    l.warn("fail mc pong", e);
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
        transportEventQueue.start();
        unicast.start(new InetSocketAddress(PORT_ANY));
        multicast.start();

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

    // FIXME (AG): remove this by creating a TCP-specific pong handler for the outgoing connection
    // one complication is that this _has_ to be the first message sent out
    // this also allows me to completely remove IUnicastCallbacks and replace resolve with an IAddressResolver
    @Override
    public void onClientCreated(ClientHandler client)
    {
        // Send a TCP_PONG so that the peer knows our listening port and our stores
        PBTPHeader pong = stores.newPongMessage(false);
        if (pong != null) client.send(TPUtil.newControl(pong));
    }

    @Override
    public void dumpStat(PBDumpStat dstemplate, PBDumpStat.Builder dsbuilder)
    {
        PBTransport tp = dstemplate.getTransport(0);
        checkNotNull(tp, "called dumpstat with null tp template");

        PBTransport.Builder tpbuilder = PBTransport.newBuilder();
        if (tp.hasName()) tpbuilder.setName(id());

        dsbuilder.addTransport(tpbuilder);

        try {
            unicast.dumpStat(dstemplate, dsbuilder);
            if (tp.hasDiagnosis()) tpbuilder.setDiagnosis("arp:\n" + arp);
        } catch (Exception e) {
            l.warn("fail dump stat", e);
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        String indent2 = indent + indentUnit;
        ps.println(indent + "q");
        transportEventQueue.dumpStatMisc(indent2, indentUnit, ps);
    }

    @Override
    public void dumpDiagnostics(GetTransportDiagnosticsReply.Builder transportDiagnostics)
    {
        transportDiagnostics.setTcpDiagnostics(getDiagnostics());
    }

    private TCPDiagnostics getDiagnostics()
    {
        TCPDiagnostics.Builder diagnostics = TCPDiagnostics.newBuilder();

        // listening port

        int listeningPort = ((InetSocketAddress) unicast.getListeningAddress()).getPort();
        PBInetSocketAddress.Builder ourAddress = PBInetSocketAddress.newBuilder();
        if (listeningPort > 0) {
            ourAddress.setHost("*").setPort(listeningPort);
        }
        diagnostics.setListeningAddress(ourAddress);

        // reachable_devices

        final List<TCPDevice> reachableDevices = newLinkedList();
        arp.visitARPEntries(new IARPVisitor()
        {
            @Override
            public void visit(DID did, ARPEntry arp)
            {
                TCPDevice device = TCPDevice.newBuilder()
                        .setDid(did.toPB())
                        .setDeviceAddress(PBInetSocketAddress.newBuilder()
                                .setHost(arp.remoteAddress
                                        .getAddress()
                                        .getHostAddress()) // always numeric
                                .setPort(arp.remoteAddress.getPort()))
                        .build();

                reachableDevices.add(device);
            }
        });
        diagnostics.addAllReachableDevices(reachableDevices);

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

    PBTPHeader newGoOfflineMessage()
    {
        return PBTPHeader.newBuilder()
                .setType(Type.TCP_GO_OFFLINE)
                .setTcpMulticastDeviceId(localdid.toPB())
                .build();
    }
}
