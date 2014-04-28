package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.daemon.transport.lib.IDevicePresenceListener;
import com.aerofs.daemon.transport.lib.IStores;
import com.aerofs.lib.Util;
import com.aerofs.lib.bf.BFSID;
import com.aerofs.lib.event.Prio;
import com.aerofs.proto.Transport.PBTCPPong;
import com.aerofs.proto.Transport.PBTCPStoresFilter;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * FIXME (AG): We equate interest with availability here (i.e. interested in store means we have it)
 */
class Stores implements IStores, IDevicePresenceListener
{
    private static final int FILTER_SEQ_INVALID = -1;

    private static class PerDeviceStoreMembership
    {
        final int _filterSeqnum; // can be Stores.FILTER_SEQ_INVALID
        final BFSID _filter;
        final ImmutableSet<SID> _onlineSids;

        private PerDeviceStoreMembership(BFSID filter, int filterSeqnum, ImmutableSet<SID> onlineSids)
        {
            _filterSeqnum = filterSeqnum;
            _filter = filter;
            _onlineSids = onlineSids;
        }
    }

    private static final Logger l = Loggers.getLogger(Stores.class);

    //
    // IMPORTANT: access to _all_ fields must be protected by synchronized (this)
    //

    private final DID _did;
    private final TCP _tcp;
    private final ARP _arp;
    private final Multicast _multicast;
    private final Map<SID, int[]> _sid2filterIndex = Maps.newTreeMap();
    private final Map<DID, PerDeviceStoreMembership> _memberships = Maps.newHashMap();

    private BFSID _filter = new BFSID(); // _filter contains all the stores the local device cares about (immutable)
    private int _filterSeq = FILTER_SEQ_INVALID;

    Stores(DID did, TCP tcp, ARP arp, Multicast multicast)
    {
        _did = did;
        _tcp = tcp;
        _arp = arp;
        _multicast = multicast;
    }

    /**
     * Creates a {@code PBTCPStoresFilter} message with a store filter and filter seq num.
     * @return a valid {@code PBTCPStoresFilter} message if a filter is set up; null if the OPM
     * filter is not set up yet
     */
    synchronized @Nullable PBTPHeader newPongMessage(boolean multicast)
    {
        if (_filterSeq == FILTER_SEQ_INVALID) return null;

        PBTPHeader.Builder bd = PBTPHeader.newBuilder()
            .setType(Type.TCP_PONG)
            .setTcpPong(PBTCPPong
                    .newBuilder()
                    .setUnicastListeningPort(_tcp.getListeningPort())
                    .setFilter(PBTCPStoresFilter
                            .newBuilder()
                            .setFilter(_filter.toPB())
                            .setSequence(_filterSeq)));

        if (multicast) bd.setTcpMulticastDeviceId(_did.toPB());
        return bd.build();
    }

    /**
     * Entry-point into the system when the local device receives a {@code PBTCPStoresFilter}.
     * This message contains a bloom filter of all store ids of the sending device.
     */
    synchronized void storesFilterReceived(DID did, PBTCPStoresFilter filterInfo)
    {
        if (!_arp.exists(did)) return;

        PerDeviceStoreMembership oldMembership = _memberships.get(did);
        if (oldMembership != null && oldMembership._filterSeqnum == filterInfo.getSequence()) return;

        l.trace("fs from d:" + did);

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
            l.debug("filter changed {} {} {} old {} {}", did, filter, filterInfo.getSequence(),
                    (oldMembership == null ? null : oldMembership._filterSeqnum), sidsOnline);
        }

        PerDeviceStoreMembership newMembership = new PerDeviceStoreMembership(
                filter,
                filterInfo.getSequence(),
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
        l.debug("notify:{} sids:{}", (online ? "online" : "offline"), sids);

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

    /**
     * Entry-point into the system when the core wants to update the list of stores that it's
     * newly-interested-in, or no-longer interested in
     */
    // FIXME (AG): we don't send presence to the core if we delete a store; this seems brittle
    @Override
    public synchronized void updateStores(SID[] addedSids, SID[] removedSids)
    {
        l.debug("update stores add:{} rem:{}", Arrays.toString(addedSids), Arrays.toString(removedSids));

        Map<SID, int[]> added = updateFilter_(addedSids, removedSids);

        updateOPMDevices_(added);
        notifyMulticastPeers_();
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
     * Called by {@link Stores#updateStores(com.aerofs.base.id.SID[], com.aerofs.base.id.SID[])}.
     * {@code updateStores} is called by the core when it changes the SIDs that it's interested in.
     * When that happens there are many devices that may have that store. This method checks all
     * devices we're currently aware of and sends presence updates to the core for every device that
     * belongs to a store that we're newly interested in.
     */
    private void updateOPMDevices_(Map<SID, int[]> added)
    {
        if (added.isEmpty()) return;

        for (Map.Entry<DID, PerDeviceStoreMembership> entry : _memberships.entrySet()) {
            DID did = entry.getKey();
            PerDeviceStoreMembership oldMembership = entry.getValue();

            ImmutableSet<SID> newlyAvailableSids = getNewlyAvailableSids_(oldMembership._filter,
                    added);

            ImmutableSet<SID> newOnlineSids = ImmutableSet.copyOf(
                    Sets.union(
                            oldMembership._onlineSids, // previously online
                            newlyAvailableSids)); // online given this updateStores call

            PerDeviceStoreMembership newMembership = new PerDeviceStoreMembership(
                    oldMembership._filter,
                    oldMembership._filterSeqnum,
                    newOnlineSids);

            updateMembership_(did, oldMembership, newMembership);
        }
    }

    /**
     * Checks existing membership information for a peer and returns the set of SIDs that we are
     * <em>newly</em> interested in. This is [(set of all SIDs we're interested in) - (set of SIDs
     * we notified the core of previously)].
     */
    private ImmutableSet<SID> getNewlyAvailableSids_(BFSID filter, Map<SID, int[]> added)
    {
        ImmutableSet.Builder<SID> bd = ImmutableSet.builder();

        for (Entry<SID, int[]> en2 : added.entrySet()) {
            SID sid = en2.getKey();
            int[] index = en2.getValue();
            if (filter.contains_(index)) bd.add(sid);
        }

        return bd.build();
    }

    // FIXME (AG): avoid calling multicast directly
    /**
     * Notify peers over multicast that the local device's filter has changed
     */
    private void notifyMulticastPeers_()
    {
        if (_filterSeq != FILTER_SEQ_INVALID) {
            do { _filterSeq++; } while (_filterSeq == FILTER_SEQ_INVALID);
        } else {
            _filterSeq = (int) (Math.random() * Integer.MAX_VALUE);
        }

        try {
            _multicast.sendControlMessage(newPongMessage(true));
        } catch (Exception e) {
            l.error("mc pong: " + Util.e(e));
        }
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
        return newPongMessage(multicast);
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
        storesFilterReceived(did, pong.getFilter());
    }

    /**
     * Process an incoming {@link PBTPHeader} of type <code>TCP_GO_OFFLINE</code>
     *
     * @param did {@link DID} of the remote peer from whom the message was received
     */
    void processGoOffline(DID did)
    {
        // noop. do nothing
    }

    //
    // IDevicePresenceListener methods
    //

    @Override
    public synchronized void onDevicePresenceChanged(DID did, boolean isPotentiallyAvailable)
    {
        if (isPotentiallyAvailable) {
            return;
        }

        PerDeviceStoreMembership membership = _memberships.remove(did);
        if (membership == null || (membership._onlineSids.isEmpty())) return;

        sendPresence_(did, false, membership._onlineSids);
    }
}
