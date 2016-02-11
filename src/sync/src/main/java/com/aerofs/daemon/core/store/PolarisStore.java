package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.IVersionUpdater;
import com.aerofs.daemon.core.collector.Collector2;
import com.aerofs.daemon.core.collector.SenderFilters;
import com.aerofs.daemon.core.net.device.Device;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.polaris.fetch.ChangeFetchScheduler;
import com.aerofs.daemon.core.polaris.fetch.ChangeNotificationSubscriber;
import com.aerofs.daemon.core.polaris.submit.ContentChangeSubmitter;
import com.aerofs.daemon.core.polaris.submit.SubmissionScheduler;
import com.aerofs.daemon.core.protocol.FilterFetcher;
import com.aerofs.daemon.core.status.PauseSync;
import com.aerofs.daemon.lib.db.PulledDeviceDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.DID;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

public class PolarisStore extends Store
{
    private final ChangeFetchScheduler _cfs;
    private final Collector2 _cf;
    private final SenderFilters _senderFilters;
    private final SubmissionScheduler<ContentChangeSubmitter> _ccss;

    public static class Factory implements Store.Factory
    {
        @Inject private Devices _devices;
        @Inject private ChangeNotificationSubscriber _cnsub;
        @Inject protected ChangeFetchScheduler.Factory _factCFS;
        @Inject protected Collector2.Factory _factCF;
        @Inject protected SenderFilters.Factory _factSF;
        @Inject protected PauseSync _pauseSync;
        @Inject private SubmissionScheduler.Factory<ContentChangeSubmitter> _factCCSS;
        @Inject private IVersionUpdater _vu;
        @Inject private PulledDeviceDatabase _pddb;
        @Inject private FilterFetcher _ff;

        public Store create_(SIndex sidx) throws SQLException
        {
            return new PolarisStore(this, sidx, _factCFS.create(sidx), _factCF.create_(sidx),
                    _factSF.create_(sidx), _vu);
        }
    }

    protected final Factory _f;

    protected PolarisStore(Factory f, SIndex sidx, ChangeFetchScheduler cfs,
                           Collector2 cf, SenderFilters sf, IVersionUpdater vu) throws SQLException
    {
        super(sidx, ImmutableMap.of(
                ChangeFetchScheduler.class, cfs,
                Collector2.class, cf,
                SenderFilters.class, sf));
        _cfs = cfs;
        _cf = cf;
        _senderFilters = sf;
        _f = f;
        _ccss = f._factCCSS.create(sidx);
        vu.addListener_((k, t) -> {
            if (k.sidx().equals(sidx) && k.cid().isContent()) _ccss.startOnCommit_(t);
        });
    }

    @Override
    public void notifyDeviceOnline_(DID did)
    {
        _f._ff.scheduleFetch_(did, _sidx);
        _cf.online_(did);
    }

    @Override
    public void notifyDeviceOffline_(DID did)
    {
        _cf.offline_(did);
    }

    public SubmissionScheduler<ContentChangeSubmitter> contentSubmitter()
    {
        return _ccss;
    }

    @Override
    public void onPauseSync_()
    {
       _cfs.stop_();
       _f._cnsub.unsubscribe_(this);
        _ccss.stop_();
    }

    @Override
    public void onResumeSync_()
    {
        _cfs.start_();
        _f._cnsub.subscribe_(this);
        _ccss.start_();
    }

    @Override
    void postCreate_()
    {
        if (!_f._pauseSync.isPaused()) {
            onResumeSync_();
        }

        _f._devices.afterAddingStore_(_sidx);

        getOnlinePotentialMemberDevices_().keySet().forEach(this::notifyDeviceOnline_);

        _f._pauseSync.addListener_(this);
    }

    @Override
    void preDelete_()
    {
        _f._pauseSync.removeListener_(this);

        if (!_f._pauseSync.isPaused()) {
            onPauseSync_();
        }

        _f._cnsub.unsubscribe_(this);

        _f._devices.beforeDeletingStore_(_sidx);

        _cf.stop_();

        // stop collector if needed
        getOnlinePotentialMemberDevices_().keySet().forEach(this::notifyDeviceOffline_);
    }

    @Override
    public void accessible_() {}

    ////////
    // OPM device management

    public Map<DID, Device> getOnlinePotentialMemberDevices_()
    {
        checkState(!_isDeleted);
        return _f._devices.getOnlinePotentialMemberDevices_(_sidx);
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        ps.println(indent + _sidx);
    }

    @Override
    public void resetCollectorFiltersForAllDevices_(Trans t)
            throws SQLException
    {
        // The local peer must "forget" that it ever pulled sender filters from any DID, so that
        // it will start from base filters in future communications.
        _f._pddb.discardAllDevices_(_sidx, t);

        for (DID did : getOnlinePotentialMemberDevices_().keySet()) {
            _f._ff.scheduleFetch_(did, _sidx);
        }
    }

    public void deletePersistentData_(Trans t)
            throws SQLException
    {
        checkState(!_isDeleted);

        _cf.deletePersistentData_(t);
        _senderFilters.deletePersistentData_(t);

        // This Store object is effectively unusable now.
        _isDeleted = true;
    }

    @Override
    public void startSubmissions() {
        super.startSubmissions();
        _ccss.start_();
    }
}
