/**
 * Created by Weihan Wang, Air Computing Inc.
 * Authors: Weihan Wang, Allen A. George
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.event.net.EOTpStartPulse;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.transport.TransportThreadGroup;
import com.aerofs.daemon.transport.lib.HdPulse;
import com.aerofs.daemon.transport.lib.ITransportImpl;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.PulseManager;
import com.aerofs.daemon.transport.lib.TransportProtocolHandler;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.daemon.transport.lib.TransportDiagnosisState;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.netty.ClientHandler;
import com.aerofs.daemon.transport.netty.IUnicastCallbacks;
import com.aerofs.daemon.transport.netty.Unicast;
import com.aerofs.daemon.transport.tcp.ARP.ARPChange;
import com.aerofs.daemon.transport.tcp.ARP.ARPEntry;
import com.aerofs.daemon.transport.tcp.ARP.IARPChangeListener;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.PBTransport;
import com.aerofs.proto.Transport.PBTCPPong;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import com.google.common.collect.Lists;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.List;
import java.util.Set;

import static com.aerofs.daemon.lib.DaemonParam.TCP.ARP_GC_INTERVAL;
import static com.aerofs.daemon.lib.DaemonParam.TCP.QUEUE_LENGTH;

public class TCP implements IUnicastCallbacks, ITransportImpl, IARPChangeListener
{
    private static final Logger l = Loggers.getLogger(TCP.class);

    private static final int PORT_ANY = 0;

    private final DID _localDID;
    private final String _id;
    private final int _pref;
    private final ARP _arp;
    private final TransportStats _transportStats;
    private final Stores _stores;
    private final Unicast _ucast;
    private final Multicast _mcast;
    private final IBlockingPrioritizedEventSink<IEvent> _sink;
    private final BlockingPrioQueue<IEvent> _q = new BlockingPrioQueue<IEvent>(QUEUE_LENGTH);
    private final Scheduler _sched;
    private final EventDispatcher _disp = new EventDispatcher();
    private final StreamManager _sm = new StreamManager();
    private final TransportDiagnosisState _tds = new TransportDiagnosisState();
    private final PulseManager _pm = new PulseManager();

    public TCP(
            UserID localUser,
            DID localDID,
            String id,
            int pref,
            IBlockingPrioritizedEventSink<IEvent> sink,
            MaxcastFilterReceiver mcfr,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory,
            ClientSocketChannelFactory clientChannelFactory,
            ServerSocketChannelFactory serverChannelFactory)
    {
        _localDID = localDID;
        _id = id;
        _pref = pref;
        _arp = new ARP();
        _transportStats = new TransportStats();
        _sched = new Scheduler(_q, id);
        _sink = sink;
        _arp.addARPChangeListener(this);
        _pm.addGenericPulseDeletionWatcher(this, _sink);
        _stores = new Stores(_localDID, this, _arp);

        _mcast = new Multicast(localDID, this, mcfr, _stores);

        // Unicast

        _ucast = new Unicast(this, _transportStats);

        TCPProtocolHandler tcpProtocolHandler = new TCPProtocolHandler(this);
        TransportProtocolHandler protocolHandler = new TransportProtocolHandler(this, sink, _sm, _pm);
        TCPBootstrapFactory bsFact = new TCPBootstrapFactory(localUser, localDID,
                clientSslEngineFactory, serverSslEngineFactory, _transportStats);
        ServerBootstrap serverBootstrap = bsFact.newServerBootstrap(serverChannelFactory, _ucast,
                tcpProtocolHandler,
                protocolHandler);
        ClientBootstrap clientBootstrap = bsFact.newClientBootstrap(clientChannelFactory);
        _ucast.setBootstraps(serverBootstrap, clientBootstrap);
    }

    @Override
    public void init_() throws Exception
    {
        // must be called *after* the Unicast object is initialized.

        TPUtil.registerCommonHandlers(this);
        TPUtil.registerMulticastHandler(this);

        _mcast.init_();

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
                    public void visit_(DID did, ARPEntry arp)
                    {
                        if (arp._lastUpdatedTimer.elapsed() > ARP_GC_INTERVAL
                                && !_ucast.isConnected(did)) {
                            evicted.add(did);
                        }
                    }
                });

                // Call remove() out of the visitor to avoid holding the ARP lock
                for (DID did : evicted) remove(did);
            }

        }, ARP_GC_INTERVAL);
    }

    @Override
    public boolean supportsMulticast()
    {
        return true;
    }

    /**
     * Call remove(did, true) for all the devices stored in the ARP
     */
    void removeAll()
    {
        final List<DID> dids = Lists.newArrayList();
        _arp.visitARPEntries(new ARP.IARPVisitor()
        {
            @Override
            public void visit_(DID did, ARPEntry arp)
            {
                dids.add(did);
            }
        });

        // Call remove() out of the visitor to avoid holding the ARP lock
        l.debug("removeAll " + dids);
        for (DID did : dids) remove(did);
    }

    /**
     * Remove a peer from ARP and disconnect from it.
     */
    private void remove(DID did)
    {
        l.info("remove: did:{}", did);
        _arp.remove(did);
        _ucast.disconnect(did, new Exception("removing peer"));
    }

    @Override
    public void start_()
    {
        _ucast.start(new InetSocketAddress(PORT_ANY));
        l.info("listening to {}", getListeningPort());
        _mcast.start_();

        new Thread(TransportThreadGroup.get(), new Runnable() {
            @Override
            public void run()
            {
                OutArg<Prio> outPrio = new OutArg<Prio>();
                while (true) {
                    IEvent ev = _q.dequeue(outPrio);
                    _disp.dispatch_(ev, outPrio.get());
                }
            }
        }, id()).start();
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

    @Override
    public EventDispatcher disp()
    {
        return _disp;
    }

    @Override
    public Scheduler sched()
    {
        return _sched;
    }

    @Override
    public HdPulse<EOTpStartPulse> sph()
    {
        return new HdPulse<EOTpStartPulse>(new StartPulse(this, _arp));
    }

    @Override
    public Unicast ucast()
    {
        return _ucast;
    }

    @Override
    public Multicast mcast()
    {
        return _mcast;
    }

    @Override
    public PulseManager pm()
    {
        return _pm;
    }

    @Override
    public StreamManager sm()
    {
        return _sm;
    }

    @Override
    public TransportDiagnosisState tds()
    {
        return _tds;
    }

    IBlockingPrioritizedEventSink<IEvent> sink()
    {
        return _sink;
    }

    @Override
    public void updateStores_(SID[] sidsAdded, SID[] sidsRemoved)
    {
        _stores.updateStores(sidsAdded, sidsRemoved);
    }

    @Override
    public void disconnect_(DID did)
    {
        _ucast.disconnect(did, new Exception("forced disconnect"));
    }

    @Override
    public void linkStateChanged_(
        Set<NetworkInterface> removed,
        Set<NetworkInterface> added,
        Set<NetworkInterface> prev,
        Set<NetworkInterface> current)
    {
        boolean becameLinkDown = !prev.isEmpty() && current.isEmpty();

        mcast().linkStateChanged(added, removed, becameLinkDown);

        // Disconnect from remote peers.
        if (becameLinkDown) {
            // We don't have to disconnect or pause accept if the the links are physically down.
            // But in case of a logical mark-down (LinkStateService#markLinksDown_()), we need
            // manual disconnection.
            ucast().pauseAccept();
            removeAll();
        }

        if (prev.isEmpty() && !current.isEmpty()) {
            ucast().resumeAccept();
        }
    }

    /**
     * @return the port that the tcp server is listening to
     */
    int getListeningPort()
    {
        return ((InetSocketAddress)_ucast.getServerAddress()).getPort();
    }

    /**
     * Processes control messages received on a <code>tcp</code> unicast channel
     *
     * @param rem {@link InetAddress} of the remote peer from which the message
     * @param did {@link DID} from which the message was received
     * @param hdr {@link com.aerofs.proto.Transport.PBTPHeader} message that was received
     * @return PBTPHeader response to be sent as a control message to the remote peer
     * @throws ExProtocolError for an unrecognized control message
     */
    PBTPHeader processMulticastControl(InetAddress rem, DID did, PBTPHeader hdr)
        throws ExProtocolError
    {
        PBTPHeader ret = null;

        switch (hdr.getType())
        {
        case TCP_PING:
            ret = processPing(true);
            break;
        case TCP_PONG:
            processPong(rem, did, hdr.getTcpPong());
            break;
        case TCP_GO_OFFLINE:
            processGoOffline(did);
            break;
        case TCP_NOP:
            break;
        default:
            throw new ExProtocolError(((Object)hdr.getType()).getClass()); // FIXME (AG): cast for IDEA12 bug
        }

        return ret;
    }

    /**
     * Process an incoming {@link PBTPHeader} of type <code>TCP_PING</code>
     *
     * @param multicast whether the message was received on a multicast channel
     * @return null in some cases, a {@link PBTPHeader} with a response to a
     * <code>TCP_PING</code>
     */
    @Nullable PBTPHeader processPing(boolean multicast)
    {
        return _stores.newPongMessage(multicast);
    }

    /**
     * Process an incoming {@link PBTCPPong}
     *
     * @param rem remote address from which the <code>TCP_PONG</code> was received
     * @param did {@link DID} of the remote peer from whom the <code>TCP_PONG</code>
     * was received
     * @param pong the <code>TCP_PONG</code> itself
     */
    void processPong(InetAddress rem, DID did, PBTCPPong pong)
    {
        InetSocketAddress isa = new InetSocketAddress(rem, pong.getUnicastListeningPort());

        _arp.put(did, isa);
        _stores.storesFilterReceived(did, pong.getFilter());
    }

    /**
     * Process an incoming {@link PBTPHeader} of type <code>TCP_GO_OFFLINE</code>
     *
     * @param did {@link DID} of the remote peer from whom the message was received
     */
    void processGoOffline(DID did)
    {
        remove(did);
    }

    //
    // IARPChangeListener methods
    //

    @Override
    public synchronized void onArpChange_(DID did, ARPChange chg)
    {
        if (chg == ARPChange.ADD) {
            l.info("rcv online presence d:" + did);
        } else if (chg == ARPChange.REM) {
            l.info("rcv offline presence d:" + did);
        }

        _pm.stopPulse(did, false);
    }

    @Override
    public SocketAddress resolve(DID did)
            throws ExDeviceOffline
    {
        return _arp.getThrows(did)._isa;
    }

    @Override
    public void closePeerStreams(DID did, boolean outbound, boolean inbound)
    {
        TPUtil.sessionEnded(new Endpoint(this, did), _sink, _sm, outbound, inbound);
    }

    @Override
    public void onClientCreated(ClientHandler client)
    {
        // Send a TCP_PONG so that the peer knows our listening port and our stores
        PBTPHeader pong = _stores.newPongMessage(true);
        if (pong != null) client.send(TPUtil.newControl(pong));
    }

    //
    // IDebug methods
    //

    @Override
    public void dumpStat(PBDumpStat dstemplate, PBDumpStat.Builder dsbuilder)
    {
        PBTransport tp = dstemplate.getTransport(0);
        assert tp != null : ("called dumpstat with null tp");

        PBTransport.Builder tpbuilder = PBTransport.newBuilder();
        if (tp.hasName()) tpbuilder.setName(id());

        dsbuilder.addTransport(tpbuilder);

        try {
            _ucast.dumpStat(dstemplate, dsbuilder);
            // Add arp
            if (tp.hasDiagnosis()) tpbuilder.setDiagnosis("arp:\n" + _arp);
        } catch (Exception e) {
            // FIXME: put in a message saying there was an error
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        String indent2 = indent + indentUnit;
        ps.println(indent + "q");
        _q.dumpStatMisc(indent2, indentUnit, ps);
    }

    //
    // utility methods
    //

    /**
     * Generate a new <code>TCP_PING</code> message
     *
     * @return {@link PBTPHeader} of type <code>TCP_PING</code>
     */
    PBTPHeader newPingMessage()
    {
        return PBTPHeader.newBuilder()
                .setType(Type.TCP_PING)
                .setTcpMulticastDeviceId(_localDID.toPB())
                .build();
    }

    PBTPHeader newGoOfflineMessage()
    {
        return PBTPHeader.newBuilder()
                .setType(Type.TCP_GO_OFFLINE)
                .setTcpMulticastDeviceId(_localDID.toPB())
                .build();
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
}
