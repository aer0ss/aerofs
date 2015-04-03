package com.aerofs.daemon.core.net.device;

import com.aerofs.base.BaseUtil;
import com.aerofs.ids.DID;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Diagnostics;
import com.aerofs.proto.Diagnostics.Transport;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

// FIXME (AG): Remove sidx management from this class
//
// this class should simply be a mapping from Device to 'available transports'
// sidcs should be managed by Devices
// a major complication in making this work is the interaction defining which stores are 'available'
// technically, Devices should simply have the following map:
// did => sid*, where sid is a union of all the sids 'available' on each transport
// while intellectually appealing, this _cannot_ currently work because of the
// way multicast is implemented in zephyr
//
// to explain, consider the following case for device D1:
// tcp online: S1, S2, S3
// zephyr online: S1, S2
//
// what stores are available on D1?
//
// the logical answer would be "S1, S2, S3", since stores are _transport independent_
// and we still have a connection via zephyr to that device. unfortunately, because
// zephyr use multicast chatrooms for each store, any multicast messages sent to S3 on
// D1 via zephyr are dropped.
//
// until we can fix this, the combination of Devices and Device will be substantially
// more complicated than they 'could' be. it's also possible that we will miss
// opportunities to communicate with devices about stores simply because of a quirk
// in our underlying multicast system
// FIXME (AG): this class _SHOULD NOT_ care about stores at all
// its concept of online and offline should only be based on whether it has connections or not
public class Device implements Comparable<Device>
{
    private final DID _did;

    private class TransportState
    {
        final Set<SIndex> _sidcsAvailable = Sets.newHashSet();

        boolean isUnused_()
        {
            return _sidcsAvailable.isEmpty();
        }

        boolean isOnlineForStore_(SIndex sidx)
        {
            return _sidcsAvailable.contains(sidx);
        }
    }

    final private SortedMap<ITransport, TransportState> _tpsAvailable =
            new TreeMap<>(Transports.PREFERENCE_COMPARATOR);

    Device(DID did)
    {
        _did = did;
    }

    public DID did()
    {
        return _did;
    }

    @Override
    public int compareTo(@Nonnull Device o)
    {
        return _did.compareTo(o._did);
    }

    /**
     * @return collection of stores that are online for this device
     */
    public Collection<SIndex> getAllKnownSidcs_()
    {
        Set<SIndex> sidcs = Sets.newHashSet();

        for (TransportState transportState : _tpsAvailable.values()) {
            sidcs.addAll(transportState._sidcsAvailable);
        }

        return sidcs;
    }

    /**
     * @return collection of stores that become online to the device
     */
    Collection<SIndex> online_(ITransport tp, Collection<SIndex> sidcs)
    {
        TransportState tpState = getOrCreate_(tp);

        List<SIndex> ret = Lists.newLinkedList();
        for (SIndex sidx : sidcs) {
            boolean added = tpState._sidcsAvailable.add(sidx); // _always_ add
            if (added) ret.add(sidx);
        }
        return getSidcsWhoseStateChanged_(tpState, ret);
    }

    /**
     * @return a collection of stores that becomes offline to the device
     */
    Collection<SIndex> offline_(ITransport tp, Collection<SIndex> sidcs)
    {
        TransportState tpState = _tpsAvailable.get(tp);
        if (tpState == null) return Collections.emptyList();

        List<SIndex> ret = Lists.newLinkedList();
        for (SIndex sidx : sidcs) {
            if (tpState._sidcsAvailable.remove(sidx)) {
                ret.add(sidx);
            }
        }

        if (tpState.isUnused_()) {
            _tpsAvailable.remove(tp);
        }

        return getSidcsWhoseStateChanged_(tpState, ret);
    }

    Collection<SIndex> offline_(ITransport tp)
    {
        TransportState en = _tpsAvailable.get(tp);
        if (en == null) {
            return Collections.emptyList();
        }

        // make a copy to avoid concurrent modification exception
        return offline_(tp, new ArrayList<>(en._sidcsAvailable));
    }

    private TransportState getOrCreate_(ITransport tp)
    {
        TransportState tpState = _tpsAvailable.get(tp);
        if (tpState == null) {
            tpState = new TransportState();
            _tpsAvailable.put(tp, tpState);
        }
        return tpState;
    }

    //
    // FIXME (AG): both versions of getSidcsWhoseStateChanged_ look similar and should be merged
    //

    /**
     * When an action (online, offline) occurs this may trigger a tpState change to an sidx:
     * an sidx that is previously available may no longer be available. This method returns
     * those sidcs that were affected as a result of an operation
     */
    private Collection<SIndex> getSidcsWhoseStateChanged_(TransportState comparedTpState,
            List<SIndex> sidcs)
    {
        Iterator<SIndex> iter = sidcs.iterator();
        while (iter.hasNext()) {
            SIndex sidx = iter.next();
            boolean isAvailable = false;
            for (TransportState tpState : _tpsAvailable.values()) {
                if (tpState != comparedTpState && tpState.isOnlineForStore_(sidx)) {
                    isAvailable = true;
                    break;
                }
            }
            if (isAvailable) iter.remove();
        }

        return sidcs;
    }

    boolean isOnline_()
    {
        return !_tpsAvailable.isEmpty();
    }

    boolean isAvailable_()
    {
        return !_tpsAvailable.isEmpty();
    }

    /**
     * TODO use device-specific preferences instead of static preference
     */
    public @Nonnull ITransport getPreferredTransport_()
    {
        ITransport preferredTransport = getPreferredTransport();

        if (preferredTransport == null) {
            // this method shouldn't get called if the device is offline on all
            // transports. this may not be a valid assumption because the daemon
            // might contact an offline peer, but so far so good
            throw SystemUtil.fatal("got on offline device");
        }

        return preferredTransport;
    }

    private @Nullable ITransport getPreferredTransport()
    {
        return _tpsAvailable.firstKey();
    }

    public int getPreferenceUtility_()
    {
        return getPreferredTransport_().rank();
    }

    @Override
    public String toString()
    {
        return _did.toString();
    }

    public @Nullable Diagnostics.Device dumpDiagnostics_()
    {
        Diagnostics.Device.Builder deviceBuilder = Diagnostics.Device.newBuilder();

        deviceBuilder.setDid(BaseUtil.toPB(did()));

        ITransport preferredTransport = getPreferredTransport();
        if (preferredTransport != null) {
            deviceBuilder.setPreferredTransportId(preferredTransport.id());
        }

        for (Entry<ITransport, TransportState> entry: _tpsAvailable.entrySet()) {
            TransportState transportState = entry.getValue();

            Diagnostics.Transport.Builder transportBuilder = Diagnostics.Transport.newBuilder();

            // id
            transportBuilder.setTransportId(entry.getKey().id());

            transportBuilder.setState(Transport.TransportState.POTENTIALLY_AVAILABLE);

            // what sidcs are 'available' on this transport
            for (SIndex sidx : transportState._sidcsAvailable) {
                transportBuilder.addKnownStoreIndexes(sidx.getInt());
            }

            deviceBuilder.addAvailableTransports(transportBuilder);
        }

        return deviceBuilder.build();
    }
}
