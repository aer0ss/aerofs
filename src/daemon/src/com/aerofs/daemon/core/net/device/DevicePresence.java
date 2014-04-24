package com.aerofs.daemon.core.net.device;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.CoreExponentialRetry;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.core.tc.CoreIMC;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.net.EOStartPulse;
import com.aerofs.daemon.event.net.EOUpdateStores;
import com.aerofs.daemon.lib.IDiagnosable;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.sched.ExponentialRetry;
import com.aerofs.proto.Diagnostics;
import com.aerofs.proto.Diagnostics.DeviceDiagnostics;
import com.aerofs.proto.Diagnostics.Store.Builder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import static com.google.common.base.Preconditions.checkNotNull;

// FIXME: what is the expected multiplicity of this class. Singleton would be super convenient.
// FIXME: what concurrency access controls are wrapped around this class?
public class DevicePresence implements IDiagnosable
{
    private static final Logger l = Loggers.getLogger(DevicePresence.class);

    private final Map<DID, Device> _did2dev = Maps.newHashMap();
    // FIXME: sidx2opm can't contain empty OPMDevice objects; wrap this map in safe mutators
    private final Map<SIndex, OPMDevices> _sidx2opm = Maps.newHashMap();
    private final List<IDevicePresenceListener> _listeners = Lists.newArrayList();

    private final Transports _tps;
    private final CoreScheduler _sched;
    private final TokenManager _tokenManager;
    private final ExponentialRetry _cer;
    private final MapSIndex2Store _sidx2s;
    private final IMapSIndex2SID _sidx2sid;

    @Inject
    public DevicePresence(TokenManager tokenManager, CoreScheduler sched, Transports tps,
            CoreExponentialRetry cer, MapSIndex2Store sidx2s, IMapSIndex2SID sidx2sid)
    {
        _tokenManager = tokenManager;
        _sched = sched;
        _tps = tps;
        _cer = cer;
        _sidx2s = sidx2s;
        _sidx2sid = sidx2sid;

        addListener_(new IDevicePresenceListener()
        {
            @Override
            public void deviceOnline_(DID did)
            {
                l.info("DPE +{}", did); // device presence edge

            }

            @Override
            public void deviceOffline_(DID did)
            {
                l.info("DPE -{}", did); // device presence edge
            }
        });
    }

    public void addListener_(IDevicePresenceListener listener)
    {
        _listeners.add(listener);
    }

    public void online_(ITransport tp, DID did, Collection<SIndex> sidcs)
    {
        l.info("online {}{} 4 {}", did, tp, sidcs);

        Device dev = _did2dev.get(did);
        if (dev == null) {
            dev = new Device(did);
            _did2dev.put(did, dev);
        }

        boolean wasFormerlyAvailable = dev.isAvailable_();

        Collection<SIndex> sidcsOnline = dev.online_(tp, sidcs);
        onlineImpl_(dev, sidcsOnline);

        if (!wasFormerlyAvailable && dev.isAvailable_()) {
            for (IDevicePresenceListener listener : _listeners) {
                listener.deviceOnline_(did);
            }
        }
    }

    public void offline_(ITransport tp, DID did, Collection<SIndex> sidcs)
    {
        Device dev = _did2dev.get(did);
        if (dev == null) return;
        l.info("offline {}{} 4 {}", did, tp, sidcs);

        boolean wasFormerlyAvailable = dev.isAvailable_();

        Collection<SIndex> sidcsOffline = dev.offline_(tp, sidcs);
        removeDIDFromStores_(did, sidcsOffline);
        if (!dev.isAvailable_()) _did2dev.remove(did);

        notifyListenersOnDeviceOfflineEdge_(did, wasFormerlyAvailable, dev.isAvailable_());
    }

    private void notifyListenersOnDeviceOfflineEdge_(DID did, boolean wasFormerlyAvailable,
            boolean isNowAvailable)
    {
        if (!isNowAvailable && wasFormerlyAvailable) {
            for (IDevicePresenceListener listener: _listeners) {
                listener.deviceOffline_(did);
            }
        }
    }

    /**
     * all the devices on the transport go offline
     */
    public void offline_(ITransport tp)
    {
        // make a copy to avoid concurrent modification exception
        ArrayList<Device> devices = Lists.newArrayList(_did2dev.values());

        for (Device dev : devices) {
            boolean wasFormerlyAvailable = dev.isAvailable_();
            Collection<SIndex> sidcsOffline = dev.offline_(tp);
            DID did = dev.did();
            removeDIDFromStores_(did, sidcsOffline);
            if (!dev.isAvailable_()) {
                _did2dev.remove(did);
            }
            notifyListenersOnDeviceOfflineEdge_(did, wasFormerlyAvailable, dev.isAvailable_());
        }
    }

    public void startPulse_(ITransport tp, DID did)
    {
        Device dev = _did2dev.get(did);
        if (dev == null) {
            l.info("{}: already offline", did);
            return;
        }

        if (dev.isBeingPulsed_(tp)) return;

        l.info("{}: start pulse", did, tp);

        boolean wasFormerlyAvailable = dev.isAvailable_();
        removeDIDFromStores_(did, dev.pulseStarted_(tp));
        notifyListenersOnDeviceOfflineEdge_(dev.did(), wasFormerlyAvailable, dev.isAvailable_());

        try {
            // we have to enqueue the event *after* the data structure is set up
            // properly, because before we resume from the blocking below,
            // the pulse stopped event may be triggered from the transport and
            // being processed by another core thread
            AbstractEBIMC ev = new EOStartPulse(_tps.getIMCE_(tp), did);
            CoreIMC.enqueueBlocking_(ev, _tokenManager);
        } catch (Exception e) {
            l.warn("{}: t:{} fail start pulse err:{}", did, tp, Util.e(e));
            pulseStopped_(tp, did);
        }
    }

    public void pulseStopped_(ITransport tp, DID did)
    {
        Device dev = _did2dev.get(did);
        if (dev == null) {
            l.warn("{}: pulse stopped but device not found", did);
            return;
        }

        Collection<SIndex> sidcsOnline = dev.pulseStopped_(tp);
        onlineImpl_(dev, sidcsOnline);
    }

    private void onlineImpl_(Device dev, Collection<SIndex> sidcs)
    {
        DID did = dev.did();
        for (SIndex sidx : sidcs) {
            Store s = _sidx2s.getNullable_(sidx);

            OPMDevices opm = _sidx2opm.get(sidx);
            if (opm == null) {
                opm = new OPMDevices();
                _sidx2opm.put(sidx, opm);
                if (s != null) {
                    s.startAntiEntropy_();
                }
            }
            opm.add_(did, dev);

            if (s != null) {
                // we map online devices in the collector as OPM devices of a member store
                s.collector().online_(did);
            }
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

            if (s != null) {
                // we map online devices in the collector as OPM devices of a member store
                s.collector().offline_(did);
            }
        }
    }

    private SID[] sindex2sid_(Collection<SIndex> sidcs)
    {
        SID[] ret = new SID[sidcs.size()];
        int count = 0;
        for (SIndex sidx : sidcs) ret[count++] = _sidx2sid.get_(sidx);
        return ret;
    }

    public void afterAddingStore_(SIndex sidx)
    {
        updateStoresForTransports_(sindex2sid_(Collections.singleton(sidx)), new SID[0]);

        Store s = _sidx2s.get_(sidx);

        // FIXME: we own the OPM table, why are we delegating this to the Store? :(
        if (!s.getOnlinePotentialMemberDevices_().isEmpty()) {
            s.startAntiEntropy_();
            for (DID did : s.getOnlinePotentialMemberDevices_().keySet()) {
                // we map online devices in the collector as OPM devices of
                // a member store
                s.collector().online_(did);
            }
        }
    }

    public void beforeDeletingStore_(SIndex sidx)
    {
        updateStoresForTransports_(new SID[0], sindex2sid_(Collections.singleton(sidx)));

        Store s = _sidx2s.get_(sidx);
        // FIXME: we own the OPM table, why are we delegating this to the Store?
        for (DID did : s.getOnlinePotentialMemberDevices_().keySet()) {
            // we map online devices in the collector as OPM devices of
            // a member store
            s.collector().offline_(did);
        }
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

    public @Nullable OPMDevices getOPMDevices_(SIndex sidx)
    {
        return _sidx2opm.get(sidx);
    }

    /**
     * This method can be called within or outside of core threads.
     */
    private void updateStoresForTransports_(final SID[] sidAdded, final SID[] sidRemoved)
    {
        final Callable<Void> callable = new Callable<Void>() {
                @Override
                public Void call() throws ExNoResource, ExAborted
                {
                    for (ITransport tp : _tps.getAll_()) {
                        EOUpdateStores ev = new EOUpdateStores(_tps.getIMCE_(tp), sidAdded, sidRemoved);
                        CoreIMC.enqueueBlocking_(ev, _tokenManager);
                    }
                    return null;
                }
            };

        // Always run the task in a separate core context as this method may be called in a
        // transaction and the CoreIMC.enqueueBlocking above may yield the core lock.
        _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                _cer.retry("updateStores", callable);
            }
        }, 0);
    }

    /**
     * Dump diagnostic information from the {@code DevicePresence} subsystem.
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
        // get the complete set of sidcs that are available _or_ being pulsed.
        //
        // we have to do this as a separate step because
        // _sidx2opm only contains the sidcs that are available,
        // which meant that if we had stores that were _only_
        // on devices being pulsed we will not dump their diagnostic information
        //
        // to make the diagnostics more useful we also include information
        // for these pulsed stores
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
            // NOTE: _all_ the DIDs associated with this sidx are removed
            // regardless of whether the device is being pulsed or not
            knownSidcs.removeAll(sidx);

            // once we're done, add the entry into the top-level message
            diagnosticsBuilder.addAvailableStores(storeBuilder);
        }

        // now, dump information for any remaining sidcs
        // these are stores that are known to devices but not available (i.e. being pulsed)
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
            storeBuilder.setSid(sid.toPB());

            // finally, add all the dids that 'have' that sidx
            for (DID did : devices) {
                storeBuilder.addKnownOnDids(did.toPB());
            }
        } catch (SQLException e) {
            l.error("fail get SID from db for sidx:{}", sidx.getInt(), e);
            storeBuilder.setSid(ByteString.EMPTY);
        }
    }
}
