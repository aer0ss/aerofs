package com.aerofs.daemon.transport.tcpmt;

import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.transport.tcpmt.ARP.ARPEntry;
import com.aerofs.lib.Util;
import com.aerofs.lib.bf.BFSID;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.aerofs.proto.Transport.PBTCPFilterAndSeq;
import com.aerofs.proto.Transport.PBTCPStores;
import com.aerofs.proto.Transport.PBTCPPong;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.protobuf.ByteString;

import org.apache.log4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.Map.Entry;

public class Stores
{
    public static class Prefix
    {
        private static int PREFIX_LENGTH = 8;
        private ByteString _pb;

        private final byte[] _bs;

        public Prefix(byte[] bs)
        {
            _bs = bs;
        }

        public Prefix(ByteString pb)
        {
            _bs = pb.toByteArray();
            _pb = pb;
        }

        public Prefix(SID sid)
        {
            _bs = Arrays.copyOf(sid.getBytes(), PREFIX_LENGTH);
        }

        public ByteString toPB()
        {
            if (_pb == null) {
                _pb = ByteString.copyFrom(_bs);
            }
            return _pb;
        }

        @Override
        public int hashCode()
        {
            return Arrays.hashCode(_bs);
        }

        @Override
        public boolean equals(Object o)
        {
            return Arrays.equals(((Prefix) o)._bs, _bs);
        }
    }

    private static final Logger l = Util.l(Stores.class);

    private final TCP _tcp;
    private final ARP _arp;
    private final HostnameMonitor _hm;

    private final static int FILTER_SEQ_INVALID = -1;
    private int _filterSeq = FILTER_SEQ_INVALID;

    // these objects contain all the stores the local device has.
    // they are immutable objects
    private BFSID _filter = new BFSID();

    // access to this object must be protected by synchronized (this)
    private final Map<SID, int[]> _sid2filterIndex =
            new TreeMap<SID, int[]>();

    // access to this object must be protected by synchronized (this)
    private final Map<Prefix, Set<SID>> _prefix2sids =
        new HashMap<Prefix, Set<SID>>();

    Stores(TCP tcp, ARP arp, HostnameMonitor hm)
    {
        this._tcp = tcp;
        this._arp = arp;
        this._hm = hm;
    }

    PBTCPStores.Builder newStoresForNonSP(DID did)
    {
        assert !Cfg.isSP();

// SP Daemon support is temporarily disabled. Search the code base for "SP_DID" and references to
// Cfg.isSP() when restoring the function.
//
//        if (did.equals(SP_DID)) {
//            synchronized (this) {
//                PBTCPStores.Builder bd = PBTCPStores.newBuilder();
//                for (Prefix prefix : _prefix2sids.keySet()) {
//                    bd.addPrefix(prefix.toPB());
//                }
//                return bd;
//            }
//        } else {
            return PBTCPStores.newBuilder()
                .setFilter(PBTCPFilterAndSeq.newBuilder()
                    .setFilter(_filter.toPB())
                    .setSequence(_filterSeq));
//        }
    }

    PBTCPStores.Builder newStoresForSP(DID did, Collection<Prefix> prefixes)
    {
        l.debug("newMS-SP " + did);
        assert Cfg.isSP();

        PBTCPStores.Builder bd = PBTCPStores.newBuilder();
        synchronized (this) {
            for (Prefix prefix : prefixes) {
                if (_prefix2sids.containsKey(prefix)) {
                    bd.addPrefix(prefix.toPB());
                }
            }
        }

        return bd;
    }

    /**
     * @return null if the OPM filter is not set up yet
     */
    PBTPHeader newPongMessage(boolean multicast)
    {
        if (_filterSeq == FILTER_SEQ_INVALID) return null;

        PBTPHeader.Builder bd = PBTPHeader.newBuilder()
            .setType(Type.TCP_PONG)
            .setTcpPong(PBTCPPong.newBuilder()
                    .setUnicastListeningPort(_tcp.ucast().getListeningPort())
                    .setFilter(PBTCPFilterAndSeq.newBuilder()
                            .setFilter(_filter.toPB())
                            .setSequence(_filterSeq)));

        if (multicast) bd.setTcpMulticastDeviceId(Cfg.did().toPB());
        return bd.build();
    }

    void storesReceived(DID did, InetAddress addr, int port,
            PBTCPStores pb, boolean multicast)
    {
        if (pb.hasFilter()) {
            filterReceived(did, addr, port, pb.getFilter(), multicast);
        } else {
            Builder<Prefix> prefixesBuilder = ImmutableSet.builder();
            for (int i = 0; i < pb.getPrefixCount(); i++) {
                prefixesBuilder.add(new Prefix(pb.getPrefix(i)));
            }
            prefixesReceived(did, addr, port, prefixesBuilder.build(), multicast);
        }
    }

    private void prefixesReceived(DID did, InetAddress addr, int port,
            ImmutableSet<Prefix> prefixes, boolean multicast)
    {
        ARPEntry arpentry = _arp.get(did);
        InetSocketAddress ep = new InetSocketAddress(addr, port);

        if (l.isDebugEnabled()) {
            l.debug("prefixes changed " + did + " " + prefixes);
        }

        ImmutableSet<SID> sidsOnline;
        synchronized (this) {
            Builder<SID> builder = ImmutableSet.builder();
            for (Prefix prefix : prefixes) {
                Set<SID> sids = _prefix2sids.get(prefix);
                if (sids != null) builder.addAll(sids);
            }
            sidsOnline = builder.build();
        }

        _arp.put(did, ep, arpentry == null ? null : arpentry._filter,
                arpentry == null ? FILTER_SEQ_INVALID : arpentry._filterSeq, prefixes, sidsOnline,
                multicast, System.currentTimeMillis());

        updatePresence(did, arpentry, sidsOnline);
    }

    /**
     * This method is thread safe
     * @param multicast whether the message is received from IP multicast
     */
    void filterReceived(DID did, InetAddress addr, int port,
            PBTCPFilterAndSeq fs, boolean multicast)
    {
        InetSocketAddress ep = new InetSocketAddress(addr, port);

        l.debug("fs from d:" + did + " m:" + multicast);

        BFSID filter;
        ImmutableSet<SID> sidsOnline;
        boolean updatePresence;

        ARPEntry arpEntry = _arp.get(did);
        if (arpEntry == null || arpEntry._filterSeq != fs.getSequence()) {
            updatePresence = true;
            filter = new BFSID(fs.getFilter());
            filter.finalize_();

            Builder<SID> builder = ImmutableSet.builder();
            synchronized (this) {
                for (Entry<SID, int[]> en : _sid2filterIndex.entrySet()) {
                    SID sid = en.getKey();
                    int[] index = en.getValue();
                    if (filter.contains_(index)) builder.add(sid);
                }
            }
            sidsOnline = builder.build();

            if (l.isDebugEnabled()) {
                l.debug("filter changed " + did + " " + filter + " " + fs.getSequence() + " old " +
                        (arpEntry == null ? null : arpEntry._filterSeq) + " " + sidsOnline);
            }

        } else {
            updatePresence = false;
            filter = arpEntry._filter;
            sidsOnline = arpEntry._sidsOnline;
        }

        _arp.put(did, ep, filter, fs.getSequence(), arpEntry == null ? null : arpEntry._prefixes,
                sidsOnline, multicast, System.currentTimeMillis());

        // really don't have to do check - can always set online
        if (arpEntry == null) _hm.online(did);
        if (updatePresence) updatePresence(did, arpEntry, sidsOnline);
    }

    private void updatePresence(DID did, ARPEntry arp, Set<SID> sids)
    {
        Set<SID> added;
        if (arp == null) {
            added = sids;
        } else {
            added = new TreeSet<SID>(sids);
            added.removeAll(arp._sidsOnline);
        }

        Set<SID> removed;
        if (arp == null) {
            removed = Collections.emptySet();
        } else {
            removed = new TreeSet<SID>(arp._sidsOnline);
            removed.removeAll(sids);
        }

        if (!added.isEmpty()) {
            // enqueue mustn't fail because we won't enqueue again after
            // arp already added the entry
            _tcp.sink().enqueueBlocking(new EIPresence(_tcp, true, did, added), Prio.LO);
        }

        if (!removed.isEmpty()) {
            // enqueue mustn't fail because we won't enqueue again after
            // arp already added the entry
            _tcp.sink().enqueueBlocking(new EIPresence(_tcp, false, did, removed), Prio.LO);
        }
    }

    void updateStores_(SID[] sidsAdded, SID[] sidsRemoved)
    {
        final Map<SID, int[]> added = new TreeMap<SID, int[]>();
        for (SID sid : sidsAdded) {
            int[] index = BFSID.HASH.hash(sid);
            added.put(sid, index);
        }

        final Set<Prefix> prefixesAdded = new HashSet<Prefix>();
        Set<Prefix> prefixesChanged = new HashSet<Prefix>();

        synchronized (this) {
            ////////
            // update the filter

            BFSID filter = sidsRemoved.length == 0 ? new BFSID(_filter) :
                new BFSID();

            _sid2filterIndex.putAll(added);
            for (SID sid : sidsRemoved) {
                _sid2filterIndex.remove(sid);
            }
            Map<SID, int[]> indics = sidsRemoved.length == 0 ? added :
                _sid2filterIndex;
            for (int[] index : indics.values()) filter.add_(index);

            filter.finalize_();
            _filter = filter;

            ////////
            // update prefixes

            for (SID sid : sidsAdded) {
                Prefix prefix = new Prefix(sid);
                Set<SID> set = _prefix2sids.get(prefix);
                if (set == null) {
                    set = new TreeSet<SID>();
                    _prefix2sids.put(prefix, set);
                }
                set.add(sid);
                prefixesAdded.add(prefix);
                prefixesChanged.add(prefix);
            }
            for (SID sid : sidsRemoved) {
                Prefix prefix = new Prefix(sid);
                Set<SID> set = _prefix2sids.get(prefix);
                if (set == null) continue;
                Util.verify(set.remove(sid));
                if (set.isEmpty()) _prefix2sids.remove(prefix);
                prefixesChanged.add(prefix);
            }
        }

        ////////
        // update OPM devices

        if (!added.isEmpty()) {
            final Map<DID, Collection<SID>> did2sids =
                    new TreeMap<DID, Collection<SID>>();

            _arp.visitARPEntries(new ARP.IARPVisitor()
            {
                @Override
                public void visit_(DID did, ARPEntry arpentry)
                {
                    ArrayList<SID> sids = new ArrayList<SID>();

                    if (arpentry._filter != null) {
                        for (Entry<SID, int[]> en2 : added.entrySet()) {
                            SID sid = en2.getKey();
                            int[] index = en2.getValue();
                            if (arpentry._filter.contains_(index)) sids.add(sid);
                        }
                    }

                    if (arpentry._prefixes != null) {
                        // pick a smaller set to iterate
                        if (arpentry._prefixes.size() > prefixesAdded.size()) {
                            for (Prefix prefix : prefixesAdded) {
                                if (arpentry._prefixes.contains(prefix)) {
                                    sids.addAll(_prefix2sids.get(prefix));
                                }
                            }
                        } else {
                            for (Prefix prefix : arpentry._prefixes) {
                                if (prefixesAdded.contains(prefix)) {
                                    sids.addAll(_prefix2sids.get(prefix));
                                }
                            }
                        }
                    }

                    if (!sids.isEmpty()) did2sids.put(did, sids);
                }
            });

            if (!did2sids.isEmpty()) {
                // N.B. for simplicity, we don't add the sids to ARPEntry._sids.
                // It will cause redundant online notification next time the remote
                // device modifies the filter (see online()), which is fine.
                _tcp.sink().enqueueBlocking(new EIPresence(_tcp, true, did2sids), Prio.LO);
            }
        }

        ////////
        // send a multicast pong and schedule periodical pongs

        if (_filterSeq != FILTER_SEQ_INVALID) {
            do { _filterSeq++; } while (_filterSeq == FILTER_SEQ_INVALID);

        } else {
            _filterSeq = (int) (Math.random() * Integer.MAX_VALUE);
            // because EOUpdateStores is issued each time the daemon
            // launches, we kick off periodical pong messages here
            _tcp.sched().schedule(new AbstractEBSelfHandling() {
                @Override
                public void handle_()
                {
                    try {
                        l.debug("arp sender: sched pong");
                        _tcp.mcast().sendControlMessage(newPongMessage(true));
                    } catch (Exception e) {
                        l.error("mc pong: " + Util.e(e));
                    }
                    _tcp.sched().schedule(this, DaemonParam.TCP.HEARTBEAT_INTERVAL);
                }
            }, DaemonParam.TCP.HEARTBEAT_INTERVAL);
        }

        try {
            // it must be done after the filter sequence is updated
            _tcp.mcast().sendControlMessage(newPongMessage(true));
        } catch (Exception e) {
            l.error("mc pong: " + Util.e(e));
        }

        ////////
        // send unicast to MUOD about the update

        PBTPHeader h = null;
        for (DID did : _arp.getMulticastUnreachableOnlineDevices()) {
            ARPEntry arpentry = _arp.get(did);
            if (arpentry == null) continue;

            if (Cfg.isSP()) {
                assert arpentry._prefixes != null;

                // send to the peer only if it's interested in the changed
                // prefixes
                boolean hasChanges = false;
                // pick a smaller set to iterate
                if (arpentry._prefixes.size() > prefixesChanged.size()) {
                    for (Prefix prefix : prefixesChanged) {
                        if (arpentry._prefixes.contains(prefix)) {
                            hasChanges = true;
                            break;
                        }
                    }
                } else {
                    for (Prefix prefix : arpentry._prefixes) {
                        if (prefixesChanged.contains(prefix)) {
                            hasChanges = true;
                            break;
                        }
                    }
                }
                if (!hasChanges) continue;

                h = PBTPHeader.newBuilder()
                        .setType(Type.TCP_STORES)
                        .setTcpStores(newStoresForSP(did, arpentry._prefixes))
                        .build();

            } else {
                if (h == null) {
                    h = PBTPHeader.newBuilder()
                            .setType(Type.TCP_STORES)
                            .setTcpStores(newStoresForNonSP(did))
                            .build();
                }
            }

            try {
                _tcp.ucast().sendControl(did, arpentry._isa, h, Prio.LO);
            } catch (Exception e) {
                // we should retry on errors because otherwise the peer will
                // never receive the update. But because an IOException usually
                // means the connection is dead, we don't bother.
                l.warn("send stores to muod, ignored: " + Util.e(e));
            }
        }
    }
}
