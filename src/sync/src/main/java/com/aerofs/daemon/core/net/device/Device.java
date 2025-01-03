package com.aerofs.daemon.core.net.device;

import com.aerofs.base.BaseUtil;
import com.aerofs.ids.DID;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Diagnostics;
import com.aerofs.proto.Diagnostics.Transport;
import com.google.common.collect.Sets;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

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

    final Set<SIndex> _sidcsAvailable = Sets.newHashSet();
    final private SortedSet<ITransport> _tpsAvailable =
            new TreeSet<>(Transports.PREFERENCE_COMPARATOR);

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
        return _sidcsAvailable;
    }

    /**
     * @return collection of stores that become online to the device
     */
    boolean online_(ITransport tp) {
        return _tpsAvailable.add(tp) && _tpsAvailable.size() == 1;
    }

    boolean join_(SIndex sidx) {
        return _sidcsAvailable.add(sidx);
    }

    /**
     * @return a collection of stores that becomes offline to the device
     */
    boolean offline_(ITransport tp) {
        return _tpsAvailable.remove(tp) && _tpsAvailable.isEmpty();
    }

    boolean leave_(SIndex sidx) {
        return _sidcsAvailable.remove(sidx);
    }

    boolean isOnline_()
    {
        return !_tpsAvailable.isEmpty();
    }

    boolean isAvailable_()
    {
        return !_tpsAvailable.isEmpty() && !_sidcsAvailable.isEmpty();
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
        Iterator<ITransport> it = _tpsAvailable.iterator();
        return it.hasNext() ? it.next() : null;
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

        for (ITransport tp: _tpsAvailable) {
            Diagnostics.Transport.Builder transportBuilder = Diagnostics.Transport.newBuilder();

            // id
            transportBuilder.setTransportId(tp.id());

            transportBuilder.setState(Transport.TransportState.POTENTIALLY_AVAILABLE);

            // what sidcs are 'available' on this transport
            for (SIndex sidx : _sidcsAvailable) {
                transportBuilder.addKnownStoreIndexes(sidx.getInt());
            }

            deviceBuilder.addAvailableTransports(transportBuilder);
        }

        return deviceBuilder.build();
    }
}
