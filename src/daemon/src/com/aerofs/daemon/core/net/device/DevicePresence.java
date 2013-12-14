package com.aerofs.daemon.core.net.device;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.CoreExponentialRetry;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.admin.Dumpables;
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
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.sched.ExponentialRetry;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import static com.google.common.base.Preconditions.checkNotNull;

public class DevicePresence implements IDumpStatMisc
{
    private static final Logger l = Loggers.getLogger(DevicePresence.class);

    private final Map<DID, Device> _did2dev = Maps.newHashMap();
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
                l.info(">>>> POTENTIALLY AVAILABLE:{}", did);

            }

            @Override
            public void deviceOffline_(DID did)
            {
                l.info(">>>> UNAVAILABLE:{}", did);
            }
        });

        Dumpables.add("dp", this);
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
            dev = new Device(did);
            _did2dev.put(did, dev);
        }

        if (dev.isBeingPulsed_(tp)) return;

        l.info("d:{} {} start pulse", did, tp);

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
            l.warn("d:{} t:{} fail start pulse err:{}", did, tp, Util.e(e));
            pulseStopped_(tp, did);
        }
    }

    public void pulseStopped_(ITransport tp, DID did)
    {
        Device dev = _did2dev.get(did);
        assert dev != null && dev.isAvailable_();

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
                    s.setOPMDevices_(opm);
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
                if (s != null) s.setOPMDevices_(null);
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

        for (DID did : s.getOnlinePotentialMemberDevices_().keySet()) {
            // we map online devices in the collector as OPM devices of
            // a member store
            s.collector().offline_(did);
        }
    }

    public Device getOPMDeviceThrows_(DID did) throws ExDeviceOffline
    {
        Device dev = getOPMDevice_(did);
        if (dev == null) throw new ExDeviceOffline();
        return dev;
    }

    /**
     * OPM = online potential member
     * @return null if the device is not online
     */
    public @Nullable Device getOPMDevice_(DID did)
    {
        Device dev = _did2dev.get(did);
        return dev != null && dev.isOnline_() ? dev : null;
    }

    public @Nullable
    OPMDevices getOPMDevices_(SIndex sidx)
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

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        ps.println(indent + "opm");
        for (Entry<SIndex, OPMDevices> en : _sidx2opm.entrySet()) {
            SIndex sidx = en.getKey();
            Store s = _sidx2s.getNullable_(sidx);
            ps.print(indent + indentUnit + sidx);
            if (s == null) ps.print("? ");
            else ps.print(": ");

            for (Device dev : en.getValue().getAll_().values()) ps.print(dev.did());
            ps.println();
        }

        ps.println(indent + "dev");
        for (Device dev : _did2dev.values()) {
            dev.dumpStatMisc(indent + indentUnit, indentUnit, ps);
        }
    }
}
