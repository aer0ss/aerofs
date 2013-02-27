/**
 * Created by Weihan Wang, Air Computing Inc.
 * Authors: Weihan Wang, Allen A. George
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.tcpmt;

import com.aerofs.base.Loggers;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.event.net.EOTpStartPulse;
import com.aerofs.daemon.event.net.EOTransportReconfigRemoteDevice;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.transport.lib.HdPulse;
import com.aerofs.daemon.transport.lib.IPipeController;
import com.aerofs.daemon.transport.lib.ITransportImpl;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.PulseManager;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.daemon.transport.lib.TransportDiagnosisState;
import com.aerofs.daemon.transport.tcpmt.ARP.ARPChange;
import com.aerofs.daemon.transport.tcpmt.ARP.ARPEntry;
import com.aerofs.daemon.transport.tcpmt.ARP.IARPChangeListener;
import com.aerofs.daemon.transport.xmpp.IPipe;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.PBTransport;
import com.aerofs.proto.Transport.PBTCPPong;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import com.aerofs.proto.Transport.PBTransportDiagnosis;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.List;
import java.util.Set;

import static com.aerofs.daemon.lib.DaemonParam.TCP.ARP_GC_INTERVAL;
import static com.aerofs.daemon.lib.DaemonParam.TCP.QUEUE_LENGTH;
import static com.aerofs.daemon.transport.lib.PulseManager.newCheckPulseReply;

public class TCP implements ITransportImpl, IPipeController, IARPChangeListener
{
    private static final Logger l = Loggers.getLogger(TCP.class);

    public static final int PORT_ANY = 0;

    private Unicast _ucast;
    private volatile boolean _ready;

    private final String _id;
    private final int _pref;
    private final MaxcastFilterReceiver _mcfr;
    private final ARP _arp = new ARP();
    private final Multicast _mcast = new Multicast(this);
    private final IBlockingPrioritizedEventSink<IEvent> _sink;
    private final BlockingPrioQueue<IEvent> _q = new BlockingPrioQueue<IEvent>(QUEUE_LENGTH);
    private final Scheduler _sched;
    private final EventDispatcher _disp = new EventDispatcher();
    private final StreamManager _sm = new StreamManager();
    private final TransportDiagnosisState _tds = new TransportDiagnosisState();
    private final Stores _stores = new Stores(Cfg.did(), this, _arp, Cfg.isSP()); // FIXME (AG): ugh. I hate Cfg
    private final HostnameMonitor _hm = new HostnameMonitor(this, _arp);
    private final PulseManager _pm = new PulseManager();

    public TCP(String id, int pref, IBlockingPrioritizedEventSink<IEvent> sink, MaxcastFilterReceiver mcfr)
    {
        _id = id;
        _pref = pref;

        _sched = new Scheduler(_q, id()); // can't initialize above because id() will return null
        _sink = sink;
        _mcfr = mcfr;

        _arp.addARPChangeListener(this);

        _pm.addGenericPulseDeletionWatcher(this, _sink);

        _disp.setHandler_(EOTransportReconfigRemoteDevice.class, new HdTransportReconfigRemoteDevice(this)); // FIXME: this should move to init()
    }

    @Override
    public void init_() throws Exception
    {
        int port;
        Integer internalPort;   // null if no internal port is specified

        String ep = Cfg.db().getNullable(Key.TCP_ENDPOINT);
        if (ep != null) {
            port = new TPUtil.HostAndPort(ep)._port;
            String epInternal = Cfg.db().getNullable(Key.TCP_INTERNAL_ENDPOINT);
            internalPort = epInternal != null ? new TPUtil.HostAndPort(epInternal)._port : null;
        } else {
            port = TCP.PORT_ANY;
            internalPort = null;
        }

        _ucast = new Unicast(this, _arp, _stores, port, internalPort);

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
                final long now = System.currentTimeMillis();
                final List<DID> evicted = Lists.newArrayList();

                _arp.visitARPEntries(new ARP.IARPVisitor()
                {
                    @Override
                    public void visit_(DID did, ARPEntry arp)
                    {
                        if (now - arp._lastUpdated > ARP_GC_INTERVAL &&
                                !_ucast.isConnected(arp._isa)) {
                            evicted.add(did);
                        }
                    }
                });

                // Call remove() out of the visitor to avoid holding the ARP lock
                for (DID did : evicted) remove(did, true);
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
        for (DID did : dids) remove(did, true);
    }

    /**
     * Remove a peer from ARP and disconnect from it.
     *
     * @param did {@link DID} of the peer to remove
     * @param notifyOffline if true, then the peer's {@link ARPEntry} is removed, and all
     * outstanding connections are killed.
     */
    private void remove(DID did, boolean notifyOffline)
    {
        l.info("remove: did:" + did + " force:" + notifyOffline);

        _hm.offline(did);

        ARPEntry arpentry = notifyOffline ? _arp.remove(did) : _arp.get(did);

        if (arpentry != null) {
            l.info("remove: disconnect connections");
            _ucast.disconnect(arpentry._isa);
        }
    }

    @Override
    public void start_()
    {
        _ucast.start_();
        _mcast.start_();
        _hm.start();

        new Thread(new Runnable() {
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

        _ready = true;
    }

    @Override
    public boolean ready()
    {
        return _ready;
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
        return new HdPulse<EOTpStartPulse>(new StartPulse(this));
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

    ARP arp()
    {
        return _arp;
    }

    HostnameMonitor hm()
    {
        return _hm;
    }

    Stores ss()
    {
        return _stores;
    }

    MaxcastFilterReceiver mcfr()
    {
        return _mcfr;
    }

    @Override
    public Set<DID> getMulticastUnreachableOnlineDevices()
    {
        return _arp.getMulticastUnreachableOnlineDevices();
    }

    @Override
    public void updateStores_(SID[] sidsAdded, SID[] sidsRemoved)
    {
        assert _ready;
        _stores.updateStores(sidsAdded, sidsRemoved);
    }

    @Override
    public void disconnect_(DID did)
    {
        remove(did, false);
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

            // TODO should we stop hostname monitor?
        }

        if (prev.isEmpty() && !current.isEmpty()) {
            ucast().resumeAccept();
        }
    }

    @Override
    public void peerConnected(DID did, IPipe.ConnectionType type, IPipe p)
    {
        assert false : "tcp unimplemented method";
    }

    @Override
    public void peerDisconnected(DID did, IPipe p)
    {
        assert false : "tcp unimplemented method";
    }

    // FIXME: refactor how we process control message - I suspect this is the first step to
    // separating this into different classes
    @Override
    public void processUnicastControl(DID did, PBTPHeader hdr)
    {
        PBTPHeader ret = null;

        try {
            switch (hdr.getType()) {
            case TCP_PING:
                ret = processPing(false);
                break;
            case TCP_GO_OFFLINE:
                processGoOffline(did);
                break;
            case TCP_NOP:
                break;
            case DIAGNOSIS:
                ret = processDiagnosis(did, hdr.getDiagnosis());
                break;
            case TRANSPORT_CHECK_PULSE_CALL:
                {
                    int pulseid = hdr.getCheckPulse().getPulseId();
                    l.info("rcv pulse req msgpulseid:" + pulseid + " d:" + did);
                    ret = newCheckPulseReply(pulseid);
                }
                break;
            case TRANSPORT_CHECK_PULSE_REPLY:
                {
                    int pulseid = hdr.getCheckPulse().getPulseId();
                    l.info("rcv pulse rep msgpulseid:" + pulseid + " d:" + did);
                    _pm.processIncomingPulseId(did, pulseid);
                }
                break;
            default:
                try {
                    ret = TPUtil.processUnicastControl(new Endpoint(this, did), hdr, _sink, _sm);
                } catch (ExNoResource e) {
                    l.warn("process ucast control, ignored d:" + did + " err:" + Util.e(e));
                    return; // no further processing necessary
                }
                break;
            }
        } catch (ExProtocolError e) {
            l.warn("unhandled msg type:" +
                hdr.getType().name() + " d:" + did + " err:" + Util.e(e));
            return; // no further processing necessary
        }

        if (ret != null) {
            try {
                _ucast.sendControl(did, ret, Prio.LO);
            } catch (Exception e) {
                l.warn("cannot send response d:" + did +
                    "rsp:" + ret.getType().name() + " err:" + Util.e(e));
            }
        }
    }

    /**
     * FIXME: refactor how we process control message - I suspect this is the first step to separating this into different classes
     *
     * Processes control messages received on a <code>tcpmt</code> unicast channel
     *
     * @param rem {@link InetAddress} of the remote peer from which the message
     * @param ep {@link Endpoint} from which the message was received
     * @param hdr {@link com.aerofs.proto.Transport.PBTPHeader} message
     * was received
     * @return PBTPHeader response to be sent as a control message to the remote peer
     * @throws ExProtocolError for an unrecognized control message
     */
    PBTPHeader processMulticastControl(InetAddress rem, Endpoint ep, PBTPHeader hdr)
        throws ExProtocolError
    {
        PBTPHeader ret = null;

        switch (hdr.getType())
        {
        case TCP_PING:
            ret = processPing(true);
            break;
        case TCP_PONG:
            processPong(rem, ep.did(), hdr.getTcpPong(), true);
            break;
        case TCP_GO_OFFLINE:
            processGoOffline(ep.did());
            break;
        case TCP_NOP:
            break;
        default:
            throw new ExProtocolError(((Object)hdr.getType()).getClass()); // FIXME (AG): cast for IDEA12 bug
        }

        return ret;
    }

    @Override
    public void processUnicastPayload(DID did, PBTPHeader hdr, ByteArrayInputStream bodyis, int wirelen)
    {
        try {
            PBTPHeader ret = TPUtil.processUnicastPayload(new Endpoint(this, did), hdr, bodyis, wirelen, _sink, _sm);
            if (ret != null) _ucast.sendControl(did, ret, Prio.LO);
        } catch (Exception e) {
            l.warn("silently discard data d:" + did + " err:" + Util.e(e)); // FIXME: is this the right thing to do?
        }
    }

    @Override
    public void closePeerStreams(DID did, boolean outbound, boolean inbound)
    {
        remove(did, false);
        TPUtil.sessionEnded(new Endpoint(this, did), _sink, _sm, outbound, inbound);
    }

    /**
     * Process an incoming {@link PBTPHeader} of type <code>TCP_PING</code>
     *
     * @param multicast whether the message was received on a multicast channel
     * @return null in some cases, a {@link PBTPHeader} with a response to a
     * <code>TCP_PING</code>
     */
    private PBTPHeader processPing(boolean multicast)
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
     * @param multicast whether this message was received on a multicast channel
     */
    private void processPong(InetAddress rem, DID did, PBTCPPong pong, boolean multicast)
    {
        InetSocketAddress isa = new InetSocketAddress(rem, pong.getUnicastListeningPort());

        _arp.put(did, isa, multicast);
        _stores.filterReceived(did, pong.getFilter());
    }

    /**
     * Process an incoming {@link PBTPHeader} of type <code>TCP_GO_OFFLINE</code>
     *
     * @param did {@link DID} of the remote peer from whom the message was received
     */
    private void processGoOffline(DID did)
    {
        remove(did, true);
    }

    /**
     * Process an incoming {@link PBTransportDiagnosis} value from a {@link PBTPHeader}
     * controll message
     *
     * @param did {@link DID} of the peer that sent the diagnostic message
     * @param dg {@link PBTransportDiagnosis} content of the control message with type
     * <code>DIAGNOSIS</code>
     * @return {@link PBTPHeader} with a response diagnostic message if required. Can
     * be <code>null</code> if no response is necessary
     * @throws ExProtocolError if the specific diagnostic message type is unrecognized
     */
    private PBTPHeader processDiagnosis(DID did, PBTransportDiagnosis dg)
        throws ExProtocolError
    {
        PBTransportDiagnosis dgret = TPUtil.processUnicastControlDiagnosis(did, dg, _ucast, _tds);
        if (dgret != null) return TPUtil.makeDiagnosis(dgret);
        return null;
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

    //
    // IDebug methods
    //

    @Override
    public void dumpStat(PBDumpStat dstemplate, PBDumpStat.Builder dsbuilder)
    {
        PBTransport tp = dstemplate.getTp(0);
        assert tp != null : ("called dumpstat with null tp");

        PBTransport.Builder tpbuilder = PBTransport.newBuilder();
        if (tp.hasName()) tpbuilder.setName(id());

        dsbuilder.addTp(tpbuilder);

        try {
            _ucast.dumpStat(dstemplate, dsbuilder);
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
    static PBTPHeader newPingMessage()
    {
        return PBTPHeader.newBuilder()
                .setType(Type.TCP_PING)
                .setTcpMulticastDeviceId(Cfg.did().toPB())
                .build();
    }

    static PBTPHeader newGoOfflineMessage()
    {
        return PBTPHeader.newBuilder()
                .setType(Type.TCP_GO_OFFLINE)
                .setTcpMulticastDeviceId(Cfg.did().toPB())
                .build();
    }
}
