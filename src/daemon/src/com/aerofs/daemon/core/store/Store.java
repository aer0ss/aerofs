package com.aerofs.daemon.core.store;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.AntiEntropy;
import com.aerofs.daemon.core.collector.Collector;
import com.aerofs.daemon.core.collector.SenderFilters;
import com.aerofs.daemon.core.net.device.Device;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.polaris.fetch.ChangeFetchScheduler;
import com.aerofs.daemon.core.polaris.fetch.ChangeNotificationSubscriber;
import com.aerofs.daemon.core.polaris.submit.MetaChangeSubmitter;
import com.aerofs.daemon.core.polaris.submit.SubmissionScheduler;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

public class Store implements Comparable<Store>, IDumpStatMisc
{
    private final SIndex _sidx;
    private final boolean _usePolaris;

    private final Collector _collector;
    private final SenderFilters _senderFilters;
    private final ChangeFetchScheduler _cfs;
    private final SubmissionScheduler<MetaChangeSubmitter> _mcss;

    // For debugging.
    // The idea is that when this.deletePersistentData_ is called, this object
    // is effectively unusable, so no callers should invoke any of its methods (except toString
    // which is harmless).  If anyone knows of a design pattern that would better fit this model
    // please change it!
    private boolean _isDeleted;

    public static class Factory
    {
        private final CfgUsePolaris _usePolaris;
        private final Devices _devices;
        private final AntiEntropy _ae;
        private final Collector.Factory _factCollector;
        private final SenderFilters.Factory _factSF;
        private final IPulledDeviceDatabase _pddb;
        private final ChangeEpochDatabase _cedb;
        private final ChangeNotificationSubscriber _cnsub;
        private final ChangeFetchScheduler.Factory _factCFS;
        private final SubmissionScheduler.Factory<MetaChangeSubmitter> _factMCSS;

        @Inject
        public Factory(
                CfgUsePolaris usePolaris,
                SenderFilters.Factory factSF,
                Collector.Factory factCollector,
                AntiEntropy ae,
                Devices devices,
                IPulledDeviceDatabase pddb,
                ChangeEpochDatabase cedb,
                ChangeNotificationSubscriber cnsub,
                ChangeFetchScheduler.Factory factCFS,
                SubmissionScheduler.Factory<MetaChangeSubmitter> factMCSS)
        {
            _usePolaris = usePolaris;
            _factSF = factSF;
            _factCollector = factCollector;
            _ae = ae;
            _devices = devices;
            _pddb = pddb;
            _cedb = cedb;
            _cnsub = cnsub;
            _factCFS = factCFS;
            _factMCSS = factMCSS;
        }

        public Store create_(SIndex sidx) throws SQLException
        {
            return new Store(this, sidx);
        }
    }

    private final Factory _f;

    private Store(Factory f, SIndex sidx) throws SQLException
    {
        _sidx = sidx;
        _collector = f._factCollector.create_(sidx);
        _senderFilters = f._factSF.create_(sidx);
        _cfs = f._factCFS.create(sidx);
        _mcss = f._factMCSS.create(sidx);
        _isDeleted = false;
        _f = f;
        _usePolaris = _f._usePolaris.get() && _f._cedb.getChangeEpoch_(_sidx) != null;
    }

    public Collector collector()
    {
        assert !_isDeleted;
        return _collector;
    }

    public SenderFilters senderFilters()
    {
        assert !_isDeleted;
        return _senderFilters;
    }

    public SIndex sidx()
    {
        assert !_isDeleted;
        return _sidx;
    }

    @Override
    public int compareTo(@Nonnull Store o)
    {
        assert !_isDeleted;
        return _sidx.compareTo(o._sidx);
    }

    @Override
    public String toString()
    {
        return _sidx.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        assert !_isDeleted;
        return this == o || (o != null && _sidx.equals(((Store) o)._sidx));
    }

    @Override
    public int hashCode()
    {
        assert !_isDeleted;
        return _sidx.hashCode();
    }

    /** Notifier called when a device becomes potentially online for this store. */
    public void notifyDeviceOnline_(DID did)
    {
        if (_usePolaris) {
            // TODO: collector equivalent
        } else {
            _collector.online_(did);
            _f._ae.request_(_sidx, did);
        }
    }

    /** Notifier called when a device becomes offline for this store. */
    public void notifyDeviceOffline_(DID did)
    {
        if (_usePolaris) {
            // TODO: collector equivalent
        } else {
            _collector.offline_(did);
        }
    }

    /**
     * Called after the Store is created.
     * If there are no OPM devices for this store, do nothing.
     */
    void postCreate_()
    {
        if (_usePolaris) {
            // start submitting local updates to polaris
            _mcss.start_();

            // start fetching updates from polaris
            _cfs.schedule_();

            // subscribe to change notifications
            _f._cnsub.subscribe_(this);
        }

        _f._devices.afterAddingStore_(_sidx);

        // make sure collector is started if peers are online
        if (hasOnlinePotentialMemberDevices_()) {
            // we map online devices in the collector as OPM devices of a member store
            getOnlinePotentialMemberDevices_().keySet().forEach(this::notifyDeviceOnline_);
        }
    }

    /**
     * Pre-deletion trigger. Before we remove a Store from the map, mark the devices offline
     * for this store.
     */
    void preDelete_()
    {
        if (_usePolaris) {
            // stop submitting local updates to polaris
            _mcss.stop_();

            // stop fetching updates from polaris
            _cfs.stop_();

            _f._cnsub.unsubscribe_(this);
        }

        _f._devices.beforeDeletingStore_(_sidx);

        // stop collector if needed
        getOnlinePotentialMemberDevices_().keySet().forEach(this::notifyDeviceOffline_);
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
        if (_usePolaris) return;
        _f._ae.start_(_sidx);
    }

    public void fetchChanges_()
    {
        checkState(_usePolaris);
        _cfs.schedule_();
    }

    /**
     * Include content components in future collection by the collector
     */
    public void startCollectingContent_(Trans t)
            throws SQLException
    {
        if (collector().includeContent_(t)) {
            // Well, since we've skipped all the content components in the collector queue, reset
            // filters so we can start collecting them.
            resetCollectorFiltersForAllDevices_(t);
        }
    }

    public void stopCollectingContent_(Trans t)
            throws SQLException
    {
        collector().excludeContent_(t);
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
