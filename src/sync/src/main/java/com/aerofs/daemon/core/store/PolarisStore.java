package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.IVersionUpdater;
import com.aerofs.daemon.core.net.device.Device;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.polaris.fetch.ChangeFetchScheduler;
import com.aerofs.daemon.core.polaris.fetch.ChangeNotificationSubscriber;
import com.aerofs.daemon.core.polaris.fetch.ContentFetcher;
import com.aerofs.daemon.core.polaris.submit.ContentChangeSubmitter;
import com.aerofs.daemon.core.polaris.submit.SubmissionScheduler;
import com.aerofs.daemon.core.status.PauseSync;
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
    private final ContentFetcher _cf;
    private final SubmissionScheduler<ContentChangeSubmitter> _ccss;

    public static class Factory implements Store.Factory
    {
        @Inject private Devices _devices;
        @Inject private ChangeNotificationSubscriber _cnsub;
        @Inject protected ChangeFetchScheduler.Factory _factCFS;
        @Inject protected ContentFetcher.Factory _factCF;
        @Inject private PauseSync _pauseSync;
        @Inject private SubmissionScheduler.Factory<ContentChangeSubmitter> _factCCSS;
        @Inject private IVersionUpdater _vu;

        public Store create_(SIndex sidx) throws SQLException
        {
            return new PolarisStore(this, sidx, _factCFS.create(sidx), _factCF.create_(sidx), _vu);
        }
    }

    private final Factory _f;

    protected PolarisStore(Factory f, SIndex sidx, ChangeFetchScheduler cfs,
                           ContentFetcher cf, IVersionUpdater vu) throws SQLException
    {
        super(sidx, ImmutableMap.of(
                ChangeFetchScheduler.class, cfs,
                ContentFetcher.class, cf));
        _cfs = cfs;
        _cf = cf;
        _f = f;
        _ccss = f._factCCSS.create(sidx);
        vu.addListener_((k, t) -> {
            if (k.sidx().equals(sidx) && k.cid().isContent()) _ccss.startOnCommit_(t);
        });
    }

    @Override
    public void notifyDeviceOnline_(DID did)
    {
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
        _ccss.start_();
        // start fetching updates from polaris
        _cfs.start_();

        // subscribe to change notifications
        _f._cnsub.subscribe_(this);

        _f._devices.afterAddingStore_(_sidx);

        getOnlinePotentialMemberDevices_().keySet().forEach(this::notifyDeviceOnline_);

        _f._pauseSync.addListener_(this);
    }

    @Override
    void preDelete_()
    {
        _ccss.stop_();
        _f._pauseSync.removeListener_(this);

        // stop fetching updates from polaris
        _cfs.stop_();
        _cf.stop_();

        _f._cnsub.unsubscribe_(this);

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
    }

    public void deletePersistentData_(Trans t)
            throws SQLException
    {
        checkState(!_isDeleted);

        // This Store object is effectively unusable now.
        _isDeleted = true;
    }
}
