package com.aerofs.daemon.transport.tcpmt;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.transport.tcpmt.ARP.ARPChange;
import com.aerofs.daemon.transport.tcpmt.ARP.IARPChangeListener;
import com.aerofs.lib.Util;
import com.aerofs.lib.bf.BFSID;
import com.aerofs.proto.Transport.PBTCPFilterAndSeq;
import com.aerofs.proto.Transport.PBTCPPong;
import com.aerofs.proto.Transport.PBTCPStores;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.in;
import static com.google.common.collect.Iterables.any;

/**
 * FIXME (AG): We equate interest with availability here (i.e. interested in store means we have it)
 * FIXME (AG): I should really split the prefix data structures into SP if possible
 * FIXME (AG): I'd like to refactor out Stores -> SPStores and NonSPStores, but there are problems
 * 1. SPStores will require a lot of state that is held in Stores. Since we're dealing with a
 *    thread-safe class here, I have to be very careful in how I design the API.
 * 2. Unicast calls newStoresForNonSP and newStoresForSP _inline_ when it receives the preamble.
 *    Since it's inline I can't use the ARPChangeListener and send a packet (since it may sit
 *    on Unicast's send queue) when it should actually go out first
 */
class Stores implements IARPChangeListener
{
    private static final int FILTER_SEQ_INVALID = -1;

    private static class Prefix
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
            this(Arrays.copyOf(sid.getBytes(), PREFIX_LENGTH));
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
            if (o == this) return true;
            else if (o == null || !(o instanceof Prefix)) return false;

            return Arrays.equals(((Prefix) o)._bs, _bs);
        }
    }

    private static class PerDeviceStoreMembership
    {
        final int _filterSeqnum; // can be Stores.FILTER_SEQ_INVALID
        final @Nullable BFSID _filter;
        final @Nullable ImmutableSet<Prefix> _prefixes;
        final ImmutableSet<SID> _onlineSids;

        private PerDeviceStoreMembership(@Nullable BFSID filter, int filterSeqnum,
                @Nullable ImmutableSet<Prefix> prefixes, ImmutableSet<SID> onlineSids)
        {
            _filterSeqnum = filterSeqnum;
            _filter = filter;
            _prefixes = prefixes;
            _onlineSids = onlineSids;
        }
    }

    private static final Logger l = Util.l(Stores.class);

    //
    // IMPORTANT: access to _all_ fields must be protected by synchronized (this)
    //

    private final DID _did;
    private final TCP _tcp;
    private final ARP _arp;
    private final boolean _sp;
    private final Map<SID, int[]> _sid2filterIndex = Maps.newTreeMap();
    private final Map<Prefix, Set<SID>> _prefix2sids = Maps.newHashMap();
    private final Map<DID, PerDeviceStoreMembership> _memberships = Maps.newHashMap();

    private BFSID _filter = new BFSID(); // _filter contains all the stores the local device cares about (immutable)
    private int _filterSeq = FILTER_SEQ_INVALID;

    Stores(DID did, TCP tcp, ARP arp, boolean sp)
    {
        this._did = did;
        this._tcp = tcp;
        this._arp = arp;
        this._sp = sp;

        _arp.addARPChangeListener(this); // FIXME (AG): not safe to leak this during construction
    }

    synchronized PBTCPStores.Builder newStoresForNonSP()
    {
        assert !_sp;

        return PBTCPStores
                .newBuilder()
                .setFilter(PBTCPFilterAndSeq
                        .newBuilder()
                        .setFilter(_filter.toPB())
                        .setSequence(_filterSeq));
    }

    /**
     * Creates a {@code PBTCPStores} message with prefixes <em>only</em>. This message should only
     * be sent by an SP daemon
     */
    synchronized PBTCPStores.Builder newStoresForSP(Collection<Prefix> prefixes)
    {
        assert _sp;

        PBTCPStores.Builder bd = PBTCPStores.newBuilder();

        for (Prefix prefix : prefixes) {
            if (_prefix2sids.containsKey(prefix)) {
                bd.addPrefix(prefix.toPB());
            }
        }

        return bd;
    }

    /**
     * Creates a {@code PBTCPStores} message with a store filter and filter seq num. This message
     * should only be sent by a <em>non-SP</em> device.
     * @return a valid {@code PBTCPStores} message if a filter is set up; null if the OPM filter is
     * not set up yet
     */
    synchronized PBTPHeader newPongMessage(boolean multicast)
    {
        if (_filterSeq == FILTER_SEQ_INVALID) return null;

        PBTPHeader.Builder bd = PBTPHeader.newBuilder()
            .setType(Type.TCP_PONG)
            .setTcpPong(PBTCPPong
                    .newBuilder()
                    .setUnicastListeningPort(_tcp.ucast().getListeningPort())
                    .setFilter(PBTCPFilterAndSeq
                            .newBuilder()
                            .setFilter(_filter.toPB())
                            .setSequence(_filterSeq)));

        if (multicast) bd.setTcpMulticastDeviceId(_did.toPB());
        return bd.build();
    }

    /**
     * Entry-point into the system when the local device receives a {@code PBTCPStores}. This
     * message can contain either prefixes or a filter.
     */
    synchronized void storesReceived(DID did, PBTCPStores pb)
    {
        if (pb.hasFilter()) {
            filterReceivedImpl_(did, pb.getFilter());
        } else {
            ImmutableSet.Builder<Prefix> bd = ImmutableSet.builder();
            for (int i = 0; i < pb.getPrefixCount(); i++) {
                bd.add(new Prefix(pb.getPrefix(i)));
            }
            prefixesReceived_(did, bd.build());
        }
    }

    /**
     * Entry-point into the system when the local device receives a {@code PBTCPStores} that contains
     * a filter/filter-seqnum
     */
    synchronized void filterReceived(DID did, PBTCPFilterAndSeq filterInfo)
    {
        filterReceivedImpl_(did, filterInfo);
    }

    private void filterReceivedImpl_(DID did, PBTCPFilterAndSeq filterInfo)
    {
        if (!_arp.exists(did)) return;

        PerDeviceStoreMembership oldMembership = _memberships.get(did);
        if (oldMembership != null && oldMembership._filterSeqnum == filterInfo.getSequence()) return;

        l.debug("fs from d:" + did);

        BFSID filter = new BFSID(filterInfo.getFilter());
        filter.finalize_();

        ImmutableSet.Builder<SID> bd = ImmutableSet.builder();

        for (Entry<SID, int[]> en : _sid2filterIndex.entrySet()) {
            SID sid = en.getKey();
            int[] index = en.getValue();
            if (filter.contains_(index)) bd.add(sid);
        }

        ImmutableSet<SID> sidsOnline = bd.build();

        if (l.isDebugEnabled()) {
            l.debug("filter changed " + did + " " + filter + " " + filterInfo.getSequence() +
                    " old " + (oldMembership == null ? null : oldMembership._filterSeqnum) +
                    " " + sidsOnline);
        }

        PerDeviceStoreMembership newMembership = new PerDeviceStoreMembership(
                filter,
                filterInfo.getSequence(),
                oldMembership == null ? null : oldMembership._prefixes,
                sidsOnline);

        updateMembership_(did, oldMembership, newMembership);
    }

    /**
     * Notifies the core if {@code did} has relevant changes (either additions or removals) to its
     * membership information.
     */
    private void updatePresence_(DID did, @Nullable PerDeviceStoreMembership oldMembership,
            PerDeviceStoreMembership newMembership)
    {
        Set<SID> added;
        if (oldMembership == null) {
            added = newMembership._onlineSids;
        } else {
            added = Sets.newHashSet(newMembership._onlineSids);
            added.removeAll(oldMembership._onlineSids);
        }

        Set<SID> removed;
        if (oldMembership == null) {
            removed = Sets.newHashSet();
        } else {
            removed = Sets.newHashSet(oldMembership._onlineSids);
            removed.removeAll(newMembership._onlineSids);
        }

        if (!added.isEmpty()) {
            sendPresence_(did, true, added);
        }

        if (!removed.isEmpty()) {
            sendPresence_(did, false, removed);
        }
    }

    private void sendPresence_(DID did, boolean online, Set<SID> sids)
    {
        if (l.isDebugEnabled()) {
            l.debug("notify prs:" + (online ? "online" : "offline") + " sids:" + sids);
        }

        // enqueue must not fail because if it does the core will not receive this presence update

        _tcp.sink().enqueueBlocking(new EIPresence(_tcp, online, did, sids), Prio.LO);
    }

    /**
     * Convenience method to update the private membership data structures and notify the core if
     * there are relevant presence changes related to {@code did}
     */
    private void updateMembership_(DID did, @Nullable PerDeviceStoreMembership oldMembership,
            PerDeviceStoreMembership newMembership)
    {
        PerDeviceStoreMembership prev = _memberships.put(did, newMembership);
        checkState(prev == oldMembership, "membership changed exp:" + oldMembership + " act:" + prev);
        updatePresence_(did, oldMembership, newMembership);
    }

    private void prefixesReceived_(DID did, ImmutableSet<Prefix> prefixes)
    {
        if (!_arp.exists(did)) return;

        if (l.isDebugEnabled()) {
            l.debug("prefixes changed " + did + " " + prefixes);
        }

        ImmutableSet.Builder<SID> bd = ImmutableSet.builder();

        for (Prefix prefix : prefixes) {
            Set<SID> sids = _prefix2sids.get(prefix);
            if (sids != null) bd.addAll(sids);
        }

        ImmutableSet<SID> onlineSids = bd.build();

        PerDeviceStoreMembership oldMembership = _memberships.get(did);
        PerDeviceStoreMembership newMembership = new PerDeviceStoreMembership(
                oldMembership == null ? null : oldMembership._filter,
                oldMembership == null ? FILTER_SEQ_INVALID : oldMembership._filterSeqnum,
                prefixes,
                onlineSids);

        updateMembership_(did, oldMembership, newMembership);
    }

    /**
     * Entry-point into the system when the core wants to update the list of stores that it's
     * newly-interested-in, or no-longer interested in
     */
    // FIXME (AG): we don't send presence to the core if we delete a store; this seems brittle
    synchronized void updateStores(SID[] addedSids, SID[] removedSids)
    {
        if (l.isDebugEnabled()) {
            l.debug("upst add:" + Arrays.toString(addedSids) +
                    " rem:" + Arrays.toString(removedSids));
        }

        Map<SID, int[]> added = updateFilter_(addedSids, removedSids);

        Set<Prefix> addedPrefixes = Sets.newHashSet();
        Set<Prefix> changedPrefixes = Sets.newHashSet();

        updatePrefixes_(addedSids, removedSids, addedPrefixes, changedPrefixes);
        updateOPMDevices_(added, addedPrefixes);
        notifyMulticastReachablePeers_();
        notifyMUODPeers_(changedPrefixes);
    }

    /**
     * Uses the updated list of stores the core is interested in to update local data structures
     * related to filters {@code _sid2filterIndex} {@code _filter}. This <em>must</em> be called
     * by {@link Stores#updateStores(com.aerofs.base.id.SID[], com.aerofs.base.id.SID[])} otherwise
     * remote peers will never be notified about our interest changes.
     */
    private Map<SID, int[]> updateFilter_(SID[] addedSids, SID[] removedSids)
    {
        Map<SID, int[]> added = Maps.newTreeMap();

        for (SID sid : addedSids) {
            int[] index = BFSID.HASH.hash(sid);
            added.put(sid, index);
        }

        BFSID filter = removedSids.length == 0 ? new BFSID(_filter) : new BFSID();

        _sid2filterIndex.putAll(added);
        for (SID sid : removedSids) {
            _sid2filterIndex.remove(sid);
        }
        Map<SID, int[]> indics = removedSids.length == 0 ? added : _sid2filterIndex;
        for (int[] index : indics.values()) filter.add_(index);

        filter.finalize_();
        _filter = filter;

        return added;
    }

    /**
     * Uses the updated list of stores the core is interested in to update local data structures
     * related to prefixes {@code _prefix2sids}. This <em>must</em> be called
     * by {@link Stores#updateStores(com.aerofs.base.id.SID[], com.aerofs.base.id.SID[])} otherwise
     * remote peers will never be notified about our interest changes.
     */
    private void updatePrefixes_(SID[] addedSids, SID[] removedSids, Set<Prefix> addedPrefixes,
            Set<Prefix> changedPrefixes)
    {
        for (SID sid : addedSids) {
            Prefix prefix = new Prefix(sid);
            Set<SID> set = _prefix2sids.get(prefix);
            if (set == null) {
                set = Sets.newTreeSet();
                _prefix2sids.put(prefix, set);
            }
            set.add(sid);
            addedPrefixes.add(prefix);
            changedPrefixes.add(prefix);
        }

        for (SID sid : removedSids) {
            Prefix prefix = new Prefix(sid);
            Set<SID> set = _prefix2sids.get(prefix);
            if (set == null) continue;
            Util.verify(set.remove(sid));
            if (set.isEmpty()) _prefix2sids.remove(prefix);
            changedPrefixes.add(prefix);
        }
    }

    /**
     * Called by {@link Stores#updateStores(com.aerofs.base.id.SID[], com.aerofs.base.id.SID[])}.
     * {@code updateStores} is called by the core when it changes the SIDs that it's interested in.
     * When that happens there are many devices that may have that store. This method checks all
     * devices we're currently aware of and sends presence updates to the core for every device that
     * belongs to a store that we're newly interested in.
     */
    private void updateOPMDevices_(Map<SID, int[]> added, Set<Prefix> addedPrefixes)
    {
        if (added.isEmpty()) return;

        for (Map.Entry<DID, PerDeviceStoreMembership> entry : _memberships.entrySet()) {
            DID did = entry.getKey();
            PerDeviceStoreMembership oldMembership = entry.getValue();

            ImmutableSet<SID> newlyAvailableSids =
                    getNewlyAvailableSids_(oldMembership, added, addedPrefixes);

            ImmutableSet<SID> newOnlineSids = ImmutableSet.copyOf(
                    Sets.union(
                    oldMembership._onlineSids, // previously online
                    newlyAvailableSids)); // online given this updateStores call

            PerDeviceStoreMembership newMembership = new PerDeviceStoreMembership(
                    oldMembership._filter,
                    oldMembership._filterSeqnum,
                    oldMembership._prefixes,
                    newOnlineSids);

            updateMembership_(did, oldMembership, newMembership);
        }
    }

    /**
     * Checks existing membership information for a peer and returns the set of SIDs that we are
     * <em>newly</em> interested in. This is [(set of all SIDs we're interested in) - (set of SIDs
     * we notified the core of previously)].
     */
    private ImmutableSet<SID> getNewlyAvailableSids_(
            PerDeviceStoreMembership membership, Map<SID, int[]> added,
            Set<Prefix> addedPrefixes)
    {
        ImmutableSet.Builder<SID> bd = ImmutableSet.builder();

        if (membership._filter != null) {
            for (Entry<SID, int[]> en2 : added.entrySet()) {
                SID sid = en2.getKey();
                int[] index = en2.getValue();
                if (membership._filter.contains_(index)) bd.add(sid);
            }
        }

        if (membership._prefixes != null) {
            if (membership._prefixes.size() > addedPrefixes.size()) {
                getCommonSids_(addedPrefixes, membership._prefixes, bd);
            } else {
                getCommonSids_(membership._prefixes, addedPrefixes, bd);
            }
        }

        return bd.build();
    }

    /**
     * Notify peers over multicast that the local device's filter has changed
     */
    private void notifyMulticastReachablePeers_()
    {
        if (_filterSeq != FILTER_SEQ_INVALID) {
            do { _filterSeq++; } while (_filterSeq == FILTER_SEQ_INVALID);
        } else {
            // FIXME (AG): pull this out of Stores and into TCP

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
            _tcp.mcast().sendControlMessage(newPongMessage(true));
        } catch (Exception e) {
            l.error("mc pong: " + Util.e(e));
        }
    }

    /**
     * Notify multicast-unavailable-online-devices of a change in the local device's filter
     */
    private void notifyMUODPeers_(Set<Prefix> changedPrefixes)
    {
        PBTPHeader hdr;
        for (DID did : _arp.getMulticastUnreachableOnlineDevices()) {
            PerDeviceStoreMembership membership = _memberships.get(did);
            if (membership == null) continue;

            if (_sp) {
                assert membership._prefixes != null;

                // send to the peer only if it's interested in the changed prefixes

                boolean hasChanges;

                // pick a smaller set to iterate
                if (membership._prefixes.size() > changedPrefixes.size()) {
                    hasChanges = existsIn_(changedPrefixes, membership._prefixes);
                } else {
                    hasChanges = existsIn_(membership._prefixes, changedPrefixes);
                }

                if (!hasChanges) continue;

                hdr = PBTPHeader
                        .newBuilder()
                        .setType(Type.TCP_STORES)
                        .setTcpStores(newStoresForSP(membership._prefixes))
                        .build();
            } else {
                hdr = PBTPHeader
                        .newBuilder()
                        .setType(Type.TCP_STORES)
                        .setTcpStores(newStoresForNonSP())
                        .build();
            }

            try {
                _tcp.ucast().sendControl(did, hdr, Prio.LO);
            } catch (Exception e) {
                // we should retry on errors because otherwise the peer will
                // never receive the update. But because an IOException usually
                // means the connection is dead, we don't bother.
                l.warn("send stores to muod, ignored: " + Util.e(e));
            }
        }
    }

    private void getCommonSids_(Set<Prefix> shorterPrefixes, Set<Prefix> longerPrefixes,
            ImmutableSet.Builder<SID> bd)
    {
        for (Prefix prefix : shorterPrefixes) {
            if (longerPrefixes.contains(prefix)) {
                bd.addAll(_prefix2sids.get(prefix));
            }
        }
    }

    /**
     * Checks if any of {@code Prefix} in {@code shorterPrefixes} exists in {@code longerPrefixes}
     */
    private boolean existsIn_(Set<Prefix> shorterPrefixes, Set<Prefix> longerPrefixes)
    {
        return any(shorterPrefixes, in(longerPrefixes));
    }

    //
    // IARPChangeListener methods
    //

    @Override
    public synchronized void onArpChange_(DID did, ARPChange chg)
    {
        if (chg != ARPChange.REM) return;

        PerDeviceStoreMembership membership = _memberships.remove(did);
        if (membership == null || (membership._onlineSids.isEmpty())) return;

        sendPresence_(did, false, membership._onlineSids);
    }
}
