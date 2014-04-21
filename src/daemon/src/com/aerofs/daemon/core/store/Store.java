package com.aerofs.daemon.core.store;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

import javax.annotation.Nullable;

import com.aerofs.daemon.core.net.device.DevicePresence;
import com.aerofs.daemon.core.net.device.OPMDevices;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.IDumpStatMisc;
import com.google.inject.Inject;

import com.aerofs.daemon.core.AntiEntropy;
import com.aerofs.daemon.core.collector.Collector;
import com.aerofs.daemon.core.collector.SenderFilters;
import com.aerofs.daemon.core.net.device.Device;
import com.aerofs.lib.Util;
import com.aerofs.base.id.DID;
import com.aerofs.lib.id.SIndex;

public class Store implements Comparable<Store>, IDumpStatMisc
{
    private final SIndex _sidx;

    // only set for partial replicas
    private long _used;

    private @Nullable OPMDevices _opm;

    private final Collector _collector;
    private final SenderFilters _senderFilters;

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

        @Inject
        public void inject_(SenderFilters.Factory factSF, Collector.Factory factCollector,
                AntiEntropy ae, DevicePresence dp)
        {

            _factSF = factSF;
            _factCollector = factCollector;
            _ae = ae;
            _dp = dp;
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
        _opm = _f._dp.getOPMDevices_(sidx);
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
    public int compareTo(Store o)
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

    ////////
    // OPM device management

    public Map<DID, Device> getOnlinePotentialMemberDevices_()
    {
        assert !_isDeleted;
        if (_opm == null) return Collections.emptyMap();
        else return _opm.getAll_();
    }

    public boolean hasOnlinePotentialMemberDevices_()
    {
        return !getOnlinePotentialMemberDevices_().isEmpty();
    }

    public void setOPMDevices_(OPMDevices opm)
    {
        assert !_isDeleted;
        _opm = opm;
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        ps.println(indent + _sidx + " used " + Util.format(_used) + "+");
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
