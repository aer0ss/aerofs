package com.aerofs.daemon.core.store;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

import javax.annotation.Nullable;

import com.aerofs.daemon.core.device.DevicePresence;
import com.google.inject.Inject;

import com.aerofs.daemon.core.AntiEntropy;
import com.aerofs.daemon.core.collector.Collector;
import com.aerofs.daemon.core.collector.SenderFilters;
import com.aerofs.daemon.core.device.Device;
import com.aerofs.daemon.core.device.OPMStore;
import com.aerofs.daemon.lib.IDumpStatMisc;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SIndex;

public class Store implements Comparable<Store>, IDumpStatMisc
{
    private final SIndex _sidx;

    // only set for partial replicas
    private long _used;

    private @Nullable OPMStore _opms;

    private final Collector _collector;
    private final SenderFilters _senderFilters;

    public static class Factory
    {
        private DevicePresence _dp;
        private IMetaDatabase _mdb;
        private AntiEntropy _ae;
        private Collector.Factory _factCollector;
        private SenderFilters.Factory _factSF;

        @Inject
        public void inject_(SenderFilters.Factory factSF,
            Collector.Factory factCollector, AntiEntropy ae,
            IMetaDatabase mdb, DevicePresence dp)
        {

            _factSF = factSF;
            _factCollector = factCollector;
            _ae = ae;
            _mdb = mdb;
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
        _collector = _f._factCollector.create_(this);
        _senderFilters = _f._factSF.create_(sidx);
        _opms = _f._dp.getOPMStore_(sidx);
        if (!Cfg.isFullReplica()) computeUsedSpace_();
    }

    public Collector collector()
    {
        return _collector;
    }

    public SenderFilters senderFilters()
    {
        return _senderFilters;
    }

    public SIndex sidx()
    {
        return _sidx;
    }

    @Override
    public int compareTo(Store o)
    {
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
        return this == o || (o != null && _sidx.equals(((Store) o)._sidx));
    }

    @Override
    public int hashCode()
    {
        return _sidx.hashCode();
    }

    ////////
    // OPM member management

    public Map<DID, Device> getOnlinePotentialMemberDevices_()
    {
        if (_opms == null) return Collections.emptyMap();
        else return _opms.getAll_();
    }

    public boolean isOnlinePotentialMemberDevice_(DID did)
    {
        return getOnlinePotentialMemberDevices_().containsKey(did);
    }

    public boolean hasOnlinePotentialMemberDevices_()
    {
        return !getOnlinePotentialMemberDevices_().isEmpty();
    }

    public void setOPMStore_(OPMStore opms)
    {
        _opms = opms;
    }

    private void computeUsedSpace_() throws SQLException
    {
        assert !Cfg.isFullReplica();
        _used = _f._mdb.getUsedSpace_(_sidx);
    }

    public boolean isOverQuota_(long extra)
    {
        assert Cfg.isFullReplica() : "change this";
        return false; //_quota - _used - extra <= 0;
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        ps.println(indent + _sidx + " used " + Util.format(_used) + "+" +
                (isOverQuota_(0) ? " (over quota)" : ""));
        _collector.dumpStatMisc(indent + indentUnit, indentUnit, ps);
    }

    private int _antiEntropySeq;

    public int getAntiEntropySeq_()
    {
        return _antiEntropySeq;
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
        _f._ae.start(_sidx, ++_antiEntropySeq);
    }
}
