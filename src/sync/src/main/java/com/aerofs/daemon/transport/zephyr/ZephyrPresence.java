package com.aerofs.daemon.transport.zephyr;

import com.aerofs.daemon.event.net.EIStoreAvailability;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.IDevicePresenceListener;
import com.aerofs.daemon.transport.presence.IStoreInterestListener;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.google.common.collect.*;

import java.util.Collection;
import java.util.Set;

import static com.aerofs.lib.event.Prio.LO;

public class ZephyrPresence implements IStoreInterestListener, IDevicePresenceListener {

    private final ITransport tp;
    private final IBlockingPrioritizedEventSink<IEvent> sink;

    private final Set<DID> onlineDevices = Sets.newHashSet();
    private final Multimap<DID, SID> multicastReachableDevices = TreeMultimap.create();

    public ZephyrPresence(ITransport tp, IBlockingPrioritizedEventSink<IEvent> sink) {
        this.tp = tp;
        this.sink = sink;
    }

    @Override
    public void onDeviceJoin(DID did, SID sid) {
        synchronized (this) {
            multicastReachableDevices.put(did, sid);
            if (!onlineDevices.contains(did)) return;
        }
        sink.enqueueBlocking(new EIStoreAvailability(tp, true, did, ImmutableList.of(sid)), LO);
    }

    @Override
    public void onDeviceLeave(DID did, SID sid) {
        synchronized (this) {
            multicastReachableDevices.remove(did, sid);
            if (!onlineDevices.contains(did)) return;
        }
        sink.enqueueBlocking(new EIStoreAvailability(tp, false, did, ImmutableList.of(sid)), LO);
    }

    @Override
    public void onDevicePresenceChanged(DID did, boolean isPotentiallyAvailable)
    {
        Collection<SID> onlineStores;

        // update onlineDevices and make a snapshot of the reachable devices for the given DID
        // lock carefully: monitor protects maps, never held while calling listeners
        synchronized (this) {
            boolean b = isPotentiallyAvailable ? onlineDevices.add(did) : onlineDevices.remove(did);
            onlineStores = ImmutableSet.copyOf(multicastReachableDevices.get(did));
        }

        if (onlineStores != null && !onlineStores.isEmpty()) {
            sink.enqueueBlocking(new EIStoreAvailability(tp, isPotentiallyAvailable, did, onlineStores), LO);
        }
    }
}
