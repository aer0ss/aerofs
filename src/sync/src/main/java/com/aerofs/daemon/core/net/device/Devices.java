package com.aerofs.daemon.core.net.device;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.lib.IDiagnosable;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Diagnostics;
import com.aerofs.proto.Diagnostics.DeviceDiagnostics;
import com.aerofs.proto.Diagnostics.Store.Builder;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Devices is responsible for managing the list of potentially-available devices,
 * mapping those to stores, and detecting edge transitions.
 *
 * "Edge transition": device or store goes from offline to online (or vice versa).
 *
 * Incoming notifications:
 *  - online/offline (come from HdStoreAvailiability only)
 *
 * Outgoing notifications:
 *  - notify Store of OPM
 *  - EOUpdateStores (I don't understand this)
 */
public class Devices implements IDiagnosable
{
    private static final Logger l = Loggers.getLogger(Devices.class);

    private final Map<DID, Device> _did2dev = Maps.newHashMap();
    // FIXME: sidx2opm can't contain empty OPMDevice objects; wrap this map in safe mutators
    private final Map<SIndex, OPMDevices> _sidx2opm = Maps.newHashMap();

    private final Transports _tps;
    private final MapSIndex2Store _sidx2s;
    private final IMapSIndex2SID _sidx2sid;

    public interface DeviceAvailabilityListener {
        void online_(DID did);
        void offline_(DID did);
    }

    private final List<DeviceAvailabilityListener> _listeners = new ArrayList<>();

    @Inject
    public Devices(
            Transports tps,
            MapSIndex2Store sidx2s,
            IMapSIndex2SID sidx2sid)
    {
        _tps = tps;
        _sidx2s = sidx2s;
        _sidx2sid = sidx2sid;
    }

    public void addListener_(DeviceAvailabilityListener l) {
        _listeners.add(l);
    }

    private Device getOrCreate_(DID did) {
        Device dev = _did2dev.get(did);
        if (dev == null) {
            dev = new Device(did);
            _did2dev.put(did, dev);
        }
        return dev;
    }

    private OPMDevices getOrCreate_(SIndex sidx) {
        OPMDevices opm = _sidx2opm.get(sidx);
        if (opm == null) {
            opm = new OPMDevices();
            _sidx2opm.put(sidx, opm);
        }
        return opm;
    }

    private void notifyOnline_(Device dev, SIndex sidx) {
        getOrCreate_(sidx).add_(dev.did(), dev);
        Store s = _sidx2s.getNullable_(sidx);
        if (s != null) s.notifyDeviceOnline_(dev.did());
    }

    public void online_(DID did, ITransport tp) {
        l.debug("{} +online {}", did, tp);
        Device dev = getOrCreate_(did);
        boolean wasFormerlyAvailable = dev.isAvailable_();
        if (dev.online_(tp)) {
            for (SIndex sidx : dev._sidcsAvailable) {
                notifyOnline_(dev, sidx);
            }
        }
        notifyListenersOnDeviceOfflineEdge_(did, wasFormerlyAvailable, dev.isAvailable_());
    }

    public void join_(DID did, SIndex sidx) {
        l.debug("{} +interest {}", did, sidx);
        Device dev = getOrCreate_(did);
        boolean wasFormerlyAvailable = dev.isAvailable_();
        if (dev.join_(sidx) && dev.isOnline_()) {
            notifyOnline_(dev, sidx);
        }
        notifyListenersOnDeviceOfflineEdge_(did, wasFormerlyAvailable, dev.isAvailable_());
    }

    private void notifyOffline_(Device dev, SIndex sidx) {
        OPMDevices d = _sidx2opm.get(sidx);
        if (d != null) d.remove_(dev.did());
        Store s = _sidx2s.getNullable_(sidx);
        if (s != null) s.notifyDeviceOffline_(dev.did());
    }

    public void offline_(DID did, ITransport tp) {
        Device dev = _did2dev.get(did);
        if (dev == null) return;
        l.debug("{} -online {}", did, tp);
        boolean wasFormerlyAvailable = dev.isAvailable_();
        if (dev.offline_(tp)) {
            for (SIndex sidx : dev._sidcsAvailable) {
                notifyOffline_(dev, sidx);
            }
        }
        notifyListenersOnDeviceOfflineEdge_(did, wasFormerlyAvailable, dev.isAvailable_());
    }

    public void leave_(DID did, SIndex sidx) {
        Device dev = _did2dev.get(did);
        if (dev == null) return;
        l.debug("{} -interest {}", did, sidx);
        boolean wasFormerlyAvailable = dev.isAvailable_();
        if (dev.leave_(sidx) && dev.isOnline_()) {
            notifyOffline_(dev, sidx);
        }
        notifyListenersOnDeviceOfflineEdge_(did, wasFormerlyAvailable, dev.isAvailable_());
    }

    private void notifyListenersOnDeviceOfflineEdge_(DID did, boolean wasFormerlyAvailable,
            boolean isNowAvailable)
    {
        if (wasFormerlyAvailable) {
            if (!isNowAvailable) {
                l.info("{} -DPE", did);
                _listeners.forEach(l -> l.offline_(did));
            }
        } else if (isNowAvailable) {
            l.info("{} +DPE", did);
            _listeners.forEach(l -> l.online_(did));
        }
    }

    /**
     * This method is responsible for state changes resulting from a device becoming online.
     *
     *  - update opm
     *  - call Store notifiers
     */
    private void onlineImpl_(Device dev, Collection<SIndex> sidcs)
    {
        DID did = dev.did();
        for (SIndex sidx : sidcs) {
            Store s = _sidx2s.getNullable_(sidx);

            OPMDevices opm = _sidx2opm.get(sidx);
            if (opm == null) {
                opm = new OPMDevices();
                _sidx2opm.put(sidx, opm);
            }
            opm.add_(did, dev);

            // "s non-null" means, "the store is/should be present locally"
            if (s != null) { s.notifyDeviceOnline_(did); }
        }
    }

    private void removeDIDFromStores_(DID did, Collection<SIndex> sidcs)
    {
        for (SIndex sidx : sidcs) {
            Store s = _sidx2s.getNullable_(sidx);

            OPMDevices opm = checkNotNull(_sidx2opm.get(sidx));
            opm.remove_(did);
            l.debug("remaining opms 4 {}: {}", sidx, opm);
            if (opm.isEmpty_()) {
                _sidx2opm.remove(sidx);
            }

            if (s != null) { s.notifyDeviceOffline_(did); }
        }
    }

    // FIXME: rename after I figure out what this means
    public void afterAddingStore_(SIndex sidx)
    {
        // TODO? _sidx2opm.put(sidx, new OPMDevices());
        updateStoresForTransports_(_sidx2sid.get_(sidx), true);
    }

    // FIXME: rename after I figure out what this means
    public void beforeDeletingStore_(SIndex sidx)
    {
        // TODO? _sidx2opm.remove(sidx);
        updateStoresForTransports_(_sidx2sid.get_(sidx), false);
    }

    // FIXME (AG): this is not an OPM
    /**
     * OPM = online potential member
     * @return null if the device is not online
     */
    public @Nullable Device getOPMDevice_(DID did)
    {
        Device dev = _did2dev.get(did);
        return dev != null && dev.isOnline_() ? dev : null;
    }

    public Map<DID, Device> getOnlinePotentialMemberDevices_(SIndex sidx)
    {
        OPMDevices opm = _sidx2opm.get(sidx);
        return (opm == null) ? Collections.emptyMap() : opm.getAll_();
    }

    /**
     * This method can be called within or outside of core threads.
     */
    private void updateStoresForTransports_(final SID sid, boolean join)
    {
        _tps.presenceSources().forEach(ps -> ps.updateInterest(sid, join));
    }

    /**
     * Dump diagnostic information from the {@code Devices} subsystem.
     * <p/>
     * This method is <strong>not</strong> thread-safe and <strong>must</strong>
     * be called with the core lock held.
     *
     * @return a valid {@link com.aerofs.proto.Diagnostics.DeviceDiagnostics} object
     * populated with available diagnostics
     */
    @Override
    public DeviceDiagnostics dumpDiagnostics_()
    {
        DeviceDiagnostics.Builder diagnosticsBuilder = DeviceDiagnostics.newBuilder();

        // FIXME (AG): consider alternatives
        //
        // get the complete set of sidcs that are available
        //
        Multimap<SIndex, DID> knownSidcs = TreeMultimap.create();

        for (Device device : _did2dev.values()) {
            Collection<SIndex> sidcs = device.getAllKnownSidcs_();
            for (SIndex sidx : sidcs) {
                knownSidcs.put(sidx, device.did());
            }
        }

        // start by adding information for all 'available' stores
        for (Entry<SIndex, OPMDevices> entry : _sidx2opm.entrySet()) {
            Builder storeBuilder = Diagnostics.Store.newBuilder();

            // add the store information
            SIndex sidx = entry.getKey();
            addStore(sidx, entry.getValue().getAll_().keySet(), storeBuilder);

            // remove this from the 'known' set, because we've already
            // dumped its diagnostic info
            knownSidcs.removeAll(sidx);

            // once we're done, add the entry into the top-level message
            diagnosticsBuilder.addAvailableStores(storeBuilder);
        }

        // now, dump information for any remaining sidcs
        // these are stores that are known to devices but not available (previously, those in pulsing)
        // don't remove the store from the known set during iteration
        for (Map.Entry<SIndex, Collection<DID>> entry: knownSidcs.asMap().entrySet()) {
            Builder storeBuilder = Diagnostics.Store.newBuilder();

            SIndex sidx = entry.getKey();
            addStore(sidx, entry.getValue(), storeBuilder);

            diagnosticsBuilder.addUnavailableStores(storeBuilder);
        }
        knownSidcs.clear(); // just to signal that we're done with this

        // finally, add all the device information
        for (Device device : _did2dev.values()) {
            diagnosticsBuilder.addDevices(device.dumpDiagnostics_());
        }

        return diagnosticsBuilder.build();
    }

    private void addStore(SIndex sidx, Collection<DID> devices, Builder storeBuilder)
    {
        try {
            // get the SID for this SIndex (will always exist)
            SID sid = _sidx2sid.getLocalOrAbsent_(sidx);

            // then, add an entry for the sidx=>sid mapping
            storeBuilder.setStoreIndex(sidx.getInt());
            storeBuilder.setSid(BaseUtil.toPB(sid));

            // finally, add all the dids that 'have' that sidx
            for (DID did : devices) {
                storeBuilder.addKnownOnDids(BaseUtil.toPB(did));
            }
        } catch (SQLException e) {
            l.error("fail get SID from db for sidx:{}", sidx.getInt(), e);
            storeBuilder.setSid(ByteString.EMPTY);
        }
    }
}
