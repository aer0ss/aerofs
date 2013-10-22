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
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.TransportThreadGroup;
import com.aerofs.daemon.transport.lib.DevicePresenceListener;
import com.aerofs.daemon.transport.lib.IUnicastCallbacks;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.PresenceService;
import com.aerofs.daemon.transport.lib.PulseManager;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.Unicast;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler.ChannelMode;
import com.aerofs.daemon.transport.lib.handlers.ClientHandler;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.daemon.transport.tcp.ARP.ARPEntry;
import com.aerofs.daemon.transport.tcp.ARP.IARPListener;
import com.aerofs.daemon.transport.tcp.ARP.IARPVisitor;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
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
import com.google.common.collect.Lists;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.slf4j.Logger;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

import static com.aerofs.daemon.lib.DaemonParam.TCP.ARP_GC_INTERVAL;
import static com.aerofs.daemon.lib.DaemonParam.TCP.HEARTBEAT_INTERVAL;
import static com.aerofs.daemon.lib.DaemonParam.TCP.QUEUE_LENGTH;
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

    private final DID _localdid;
    private final String _id;
    private final int _pref;
    private final ARP _arp;
    private final TransportStats _transportStats;
    private final Stores _stores;
    private final Unicast _unicast;
    private final Multicast _multicast;
    private final IBlockingPrioritizedEventSink<IEvent> _sink;
    private final BlockingPrioQueue<IEvent> _q = new BlockingPrioQueue<IEvent>(QUEUE_LENGTH);
    private final Scheduler _sched;
    private final EventDispatcher _disp = new EventDispatcher();
    private final StreamManager _streamManager = new StreamManager();
    private final PulseManager _pulseManager = new PulseManager();
    private final PresenceService _presenceService = new PresenceService();

    public TCP(
            UserID localUser,
            DID localdid,
            String id,
            int pref,
            IBlockingPrioritizedEventSink<IEvent> sink,
            LinkStateService linkStateService,
            boolean listenToMulticastOnLoopback,
            MaxcastFilterReceiver maxcastFilterReceiver,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory,
            RockLog rockLog,
            ClientSocketChannelFactory clientChannelFactory,
            ServerSocketChannelFactory serverChannelFactory)
    {
        _localdid = localdid;
        _id = id;
        _pref = pref;
        _arp = new ARP();
        _transportStats = new TransportStats();
        _sched = new Scheduler(_q, id + "-sched");
        _sink = sink;

        _multicast = new Multicast(localdid, this, listenToMulticastOnLoopback, maxcastFilterReceiver);
        linkStateService.addListener(_multicast, sameThreadExecutor()); // can notify on the link-state thread because Multicast is thread-safe

        _stores = new Stores(_localdid, this, _arp, _multicast);
        _multicast.setStores(_stores);

        // Unicast
        _unicast = new Unicast(this, _transportStats);
        ChannelTeardownHandler serverChannelTeardownHandler = new ChannelTeardownHandler(this, _sink, _streamManager, ChannelMode.SERVER);
        ChannelTeardownHandler clientChannelTeardownHandler = new ChannelTeardownHandler(this, _sink, _streamManager, ChannelMode.CLIENT);
        TCPProtocolHandler tcpProtocolHandler = new TCPProtocolHandler(_stores, _unicast);
        TransportProtocolHandler protocolHandler = new TransportProtocolHandler(this, sink, _streamManager, _pulseManager, _unicast);
        TCPBootstrapFactory bsFact = new TCPBootstrapFactory(_id, localUser, localdid, clientSslEngineFactory, serverSslEngineFactory, _presenceService, rockLog, _transportStats);
        ServerBootstrap serverBootstrap = bsFact.newServerBootstrap(serverChannelFactory, _unicast, tcpProtocolHandler, protocolHandler, serverChannelTeardownHandler);
        ClientBootstrap clientBootstrap = bsFact.newClientBootstrap(clientChannelFactory, clientChannelTeardownHandler);
        _unicast.setBootstraps(serverBootstrap, clientBootstrap);

        // Presence Service
        _unicast.setUnicastListener(_presenceService);
        _multicast.setListener(_presenceService);
        _presenceService.addListener(new DevicePresenceListener(id, _unicast, _pulseManager, rockLog));
        _arp.addListener(new IARPListener()
        {
            @Override
            public void onARPEntryChange(DID did, boolean added)
            {
                if (added) {
                    _presenceService.onDeviceReachable(did);
                } else {
                    _presenceService.onDeviceUnreachable(did);
                }
            }
        });
    }

    @Override
    public void init_() throws Exception
    {
        // must be called *after* the Unicast object is initialized.

        setupCommonHandlersAndListeners(this, _disp, _sched, _sink, _stores, _streamManager, _pulseManager, _unicast, _presenceService);
        setupMulticastHandler(_disp, _multicast);

        _multicast.init();

        _sched.schedule(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                arpGC();

                _sched.schedule(this, ARP_GC_INTERVAL);
            }

            private void arpGC()
            {
                final List<DID> evicted = Lists.newArrayList();

                _arp.visitARPEntries(new ARP.IARPVisitor()
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
                    _arp.remove(did);
                }
            }

        }, ARP_GC_INTERVAL);

        _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                try {
                    l.debug("arp sender: sched pong");

                    PBTPHeader pong = _stores.newPongMessage(true);
                    if (pong != null) {
                        _multicast.sendControlMessage(pong);
                    }
                } catch (Exception e) {
                    l.warn("fail mc pong", e);
                }

                _sched.schedule(this, HEARTBEAT_INTERVAL);
            }
        }, HEARTBEAT_INTERVAL);
    }

    @Override
    public boolean supportsMulticast()
    {
        return true;
    }

    @Override
    public void start_()
    {
        _unicast.start(new InetSocketAddress(PORT_ANY));
        l.info("listening to {}", getListeningPort());
        _multicast.start();

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
    }

    @Override
    public String id()
    {
        return _id;
    }

    @Override
    public String toString()
    {
        return id();
    }

    @Override
    public int rank()
    {
        return _pref;
    }

    @Override
    public IBlockingPrioritizedEventSink<IEvent> q()
    {
        return _q;
    }

    IBlockingPrioritizedEventSink<IEvent> sink()
    {
        return _sink;
    }

    /**
     * @return the port that the tcp server is listening to
     */
    int getListeningPort()
    {
        return ((InetSocketAddress)_unicast.getListeningAddress()).getPort();
    }

    @Override
    public SocketAddress resolve(DID did)
            throws ExDeviceOffline
    {
        return _arp.getThrows(did).remoteAddress;
    }

    // FIXME (AG): remove this by creating a TCP-specific pong handler for the outgoing connection
    // one complication is that this _has_ to be the first message sent out
    // this also allows me to completely remove IUnicastCallbacks and replace resolve with an IAddressResolver
    @Override
    public void onClientCreated(ClientHandler client)
    {
        // Send a TCP_PONG so that the peer knows our listening port and our stores
        PBTPHeader pong = _stores.newPongMessage(false);
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
            _unicast.dumpStat(dstemplate, dsbuilder);
            if (tp.hasDiagnosis()) tpbuilder.setDiagnosis("arp:\n" + _arp);
        } catch (Exception e) {
            l.warn("fail dump stat", e);
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        String indent2 = indent + indentUnit;
        ps.println(indent + "q");
        _q.dumpStatMisc(indent2, indentUnit, ps);
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

        int listeningPort = ((InetSocketAddress) _unicast.getListeningAddress()).getPort();
        PBInetSocketAddress.Builder ourAddress = PBInetSocketAddress.newBuilder();
        if (listeningPort > 0) {
            ourAddress.setHost("*").setPort(listeningPort);
        }
        diagnostics.setListeningAddress(ourAddress);

        // reachable_devices

        final List<TCPDevice> reachableDevices = newLinkedList();
        _arp.visitARPEntries(new IARPVisitor()
        {
            @Override
            public void visit(DID did, ARPEntry arp)
            {
                TCPDevice device = TCPDevice
                        .newBuilder()
                        .setDid(did.toPB())
                        .setDeviceAddress(PBInetSocketAddress
                                .newBuilder()
                                .setHost(arp.remoteAddress.getAddress().getHostAddress()) // always numeric
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
        return _transportStats.getBytesReceived();
    }

    @Override
    public long bytesOut()
    {
        return _transportStats.getBytesSent();
    }

    PBTPHeader newGoOfflineMessage()
    {
        return PBTPHeader.newBuilder()
                .setType(Type.TCP_GO_OFFLINE)
                .setTcpMulticastDeviceId(_localdid.toPB())
                .build();
    }
}
