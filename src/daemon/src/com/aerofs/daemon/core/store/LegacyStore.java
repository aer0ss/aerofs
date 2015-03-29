package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.protocol.NewUpdatesSender;
import com.aerofs.ids.DID;
import com.aerofs.daemon.core.AntiEntropy;
import com.aerofs.daemon.core.collector.Collector;
import com.aerofs.daemon.core.collector.SenderFilters;
import com.aerofs.daemon.core.net.device.Device;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.status.PauseSync;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

public class LegacyStore extends Store
{
    private final Collector _collector;
    private final SenderFilters _senderFilters;

    public static class Factory implements Store.Factory
    {
        private final Devices _devices;
        private final AntiEntropy _ae;
        private final NewUpdatesSender _nus;
        private final Collector.Factory _factCollector;
        private final SenderFilters.Factory _factSF;
        private final IPulledDeviceDatabase _pddb;
        private final PauseSync _pauseSync;

        @Inject
        public Factory(
                SenderFilters.Factory factSF,
                Collector.Factory factCollector,
                AntiEntropy ae,
                NewUpdatesSender nus,
                Devices devices,
                IPulledDeviceDatabase pddb,
                PauseSync pauseSync)
        {
            _factSF = factSF;
            _factCollector = factCollector;
            _ae = ae;
            _nus = nus;
            _devices = devices;
            _pddb = pddb;
            _pauseSync = pauseSync;
        }

        public Store create_(SIndex sidx) throws SQLException
        {
            return new LegacyStore(this, sidx,
                    _factCollector.create_(sidx), _factSF.create_(sidx));
        }
    }

    private final Factory _f;

    private LegacyStore(Factory f, SIndex sidx, Collector collector, SenderFilters senderFilters)
    {
        super(sidx, ImmutableMap.of(
                Collector.class, collector,
                SenderFilters.class, senderFilters));
        _collector = collector;
        _senderFilters = senderFilters;
        _f = f;
    }

    @Override
    public void notifyDeviceOnline_(DID did)
    {
        _collector.online_(did);
        _f._ae.request_(_sidx, did);
    }

    @Override
    public void notifyDeviceOffline_(DID did)
    {
        _collector.offline_(did);
    }

    /**
     * Called after the Store is created.
     * If there are no OPM devices for this store, do nothing.
     */
    @Override
    void postCreate_()
    {
        _f._devices.afterAddingStore_(_sidx);

        getOnlinePotentialMemberDevices_().keySet().forEach(this::notifyDeviceOnline_);

        _f._pauseSync.addListener_(this);
    }

    /**
     * Pre-deletion trigger. Before we remove a Store from the map, mark the devices offline
     * for this store.
     */
    @Override
    void preDelete_()
    {
        _f._pauseSync.removeListener_(this);

        _f._devices.beforeDeletingStore_(_sidx);

        // stop collector if needed
        getOnlinePotentialMemberDevices_().keySet().forEach(this::notifyDeviceOffline_);
    }

    @Override
    public void accessible_() {
        startAntiEntropy_();
        try {
            _f._nus.sendForStore_(_sidx, null);
        } catch (Exception e) {}
    }

    ////////
    // OPM device management

    public Map<DID, Device> getOnlinePotentialMemberDevices_()
    {
        checkState(!_isDeleted);
        return _f._devices.getOnlinePotentialMemberDevices_(_sidx);
    }

    public boolean hasOnlinePotentialMemberDevices_()
    {
        return _f._devices.getOPMDevices_(_sidx) != null;
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        ps.println(indent + _sidx);
        _collector.dumpStatMisc(indent + indentUnit, indentUnit, ps);
    }

    /**
     * Start or restart periodical anti-entropy pulling. The first pull is performed immediately.
     *
     * This method should be called when:
     *
     * 1) the store is added while there are non-zero online-potential-member devices (OPM), or
     * 2) the first OPM device is added, or
     * 3) the client wants to perform an immediate anti-entropy pull.
     */
    public void startAntiEntropy_()
    {
        _f._ae.start_(_sidx);
    }

    /**
     * Include content components in future collection by the collector
     */
    public void startCollectingContent_(Trans t)
            throws SQLException
    {
        if (iface(Collector.class).includeContent_(t)) {
            // Well, since we've skipped all the content components in the collector queue, reset
            // filters so we can start collecting them.
            resetCollectorFiltersForAllDevices_(t);
        }
    }

    public void stopCollectingContent_(Trans t)
            throws SQLException
    {
        iface(Collector.class).excludeContent_(t);
    }

    /**
     * Call this method to recollect of all the components in the collector queue. It is useful if
     * some components are in the queue but their filters have been deleted. It may happen if the
     * components were skipped during previous collection, or were manually added to the queue out
     * of normal P2P communication. See the method's callers for example use cases.
     *
     * N.B. This method resets the collector to use base filters and thus may it may worsen the
     * impact of ghost KMLs. Use with extreme caution. TODO (WW) fix this problem.
     */
    public void resetCollectorFiltersForAllDevices_(Trans t)
            throws SQLException
    {
        // The local peer must "forget" that it ever pulled sender filters from any DID, so that
        // it will start from base filters in future communications.
        _f._pddb.discardAllDevices_(_sidx, t);

        // perform an immediate anti-entropy pulling to receive new collector filters and thus
        // trigger collection.
        startAntiEntropy_();
    }

    public void deletePersistentData_(Trans t)
            throws SQLException
    {
        assert !_isDeleted;
        _collector.deletePersistentData_(t);
        _senderFilters.deletePersistentData_(t);

        // This Store object is effectively unusable now.
        _isDeleted = true;
    }
}
