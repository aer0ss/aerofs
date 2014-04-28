package com.aerofs.daemon.core.store;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.AntiEntropy;
import com.aerofs.daemon.core.collector.Collector;
import com.aerofs.daemon.core.collector.SenderFilters;
import com.aerofs.daemon.core.net.device.Device;
import com.aerofs.daemon.core.net.device.DevicePresence;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Map;

public class Store implements Comparable<Store>, IDumpStatMisc
{
    private final SIndex _sidx;

    private final Collector _collector;
    private final SenderFilters _senderFilters;
    private final DevicePresence _dp;

    // For debugging.
    // The idea is that when this.deletePersistentData_ is called, this object
    // is effectively unusable, so no callers should invoke any of its methods (except toString
    // which is harmless).  If anyone knows of a design pattern that would better fit this model
    // please change it!
    private boolean _isDeleted;

    public static class Factory
    {
        private DevicePresence _dp;
        private AntiEntropy _ae;
        private Collector.Factory _factCollector;
        private SenderFilters.Factory _factSF;
        private IPulledDeviceDatabase _pddb;

        @Inject
        public void inject_(SenderFilters.Factory factSF, Collector.Factory factCollector,
                AntiEntropy ae, DevicePresence dp, IPulledDeviceDatabase pddb)
        {
            _factSF = factSF;
            _factCollector = factCollector;
            _ae = ae;
            _dp = dp;
            _pddb = pddb;
        }

        public Store create_(SIndex sidx) throws SQLException
        {
            return new Store(this, sidx);
        }
    }

    private final Factory _f;

    private Store(Factory f, SIndex sidx) throws SQLException
    {
        _f = f;
        _sidx = sidx;
        _collector = _f._factCollector.create_(sidx);
        _senderFilters = _f._factSF.create_(sidx);
        _dp = _f._dp;
        _isDeleted = false;
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
    public void notifyDeviceOnline(DID did) {
        _collector.online_(did);
        _f._ae.request_(_sidx, did);
    }

    /** Notifier called when a device becomes offline for this store. */
    public void notifyDeviceOffline(DID did) { _collector.offline_(did); }

    /**
     * Called after the Store is created.
     * If there are no OPM devices for this store, do nothing.
     */
    void postCreate()
    {
        // TODO: is this necessary? Perhaps not.
        if (hasOnlinePotentialMemberDevices_()) {
            // we map online devices in the collector as OPM devices of a member store
            for (DID did : getOnlinePotentialMemberDevices_().keySet()) {
                notifyDeviceOnline(did);
            }
        }
    }

    /**
     * Pre-deletion trigger. Before we remove a Store from the map, mark the devices offline
     * for this store.
     */
    void preDelete()
    {
        // TODO: is this necessary? Perhaps not.
        for (DID did : _dp.getOnlinePotentialMemberDevices_(_sidx).keySet()) {
            notifyDeviceOffline(did);
        }
    }

    ////////
    // OPM device management

    public Map<DID, Device> getOnlinePotentialMemberDevices_()
    {
        assert !_isDeleted;
        return _dp.getOnlinePotentialMemberDevices_(_sidx);
    }

    public boolean hasOnlinePotentialMemberDevices_()
    {
        return _dp.getOPMDevices_(_sidx) != null;
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
