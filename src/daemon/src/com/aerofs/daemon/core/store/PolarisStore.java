package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.net.device.Device;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.polaris.fetch.ChangeFetchScheduler;
import com.aerofs.daemon.core.polaris.fetch.ChangeNotificationSubscriber;
import com.aerofs.daemon.core.polaris.fetch.ContentFetcher;
import com.aerofs.daemon.core.polaris.submit.ContentChangeSubmitter;
import com.aerofs.daemon.core.polaris.submit.MetaChangeSubmitter;
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
    // TODO: submit fields should be optional
    // -> subclass?
    private final SubmissionScheduler<MetaChangeSubmitter> _mcss;
    private final SubmissionScheduler<ContentChangeSubmitter> _ccss;
    private final ContentFetcher _cf;

    public static class Factory implements Store.Factory
    {
        private final Devices _devices;
        private final ChangeNotificationSubscriber _cnsub;
        private final ChangeFetchScheduler.Factory _factCFS;
        private final SubmissionScheduler.Factory<MetaChangeSubmitter> _factMCSS;
        private final SubmissionScheduler.Factory<ContentChangeSubmitter> _factCCSS;
        private final ContentFetcher.Factory _factCF;
        private final PauseSync _pauseSync;

        @Inject
        public Factory(
                Devices devices,
                ChangeNotificationSubscriber cnsub,
                ChangeFetchScheduler.Factory factCFS,
                SubmissionScheduler.Factory<MetaChangeSubmitter> factMCSS,
                SubmissionScheduler.Factory<ContentChangeSubmitter> factCCSS,
                ContentFetcher.Factory factCF,
                PauseSync pauseSync)
        {
            _devices = devices;
            _cnsub = cnsub;
            _factCFS = factCFS;
            _factMCSS = factMCSS;
            _factCCSS = factCCSS;
            _factCF = factCF;
            _pauseSync = pauseSync;
        }

        public Store create_(SIndex sidx) throws SQLException
        {
            return new PolarisStore(this, sidx, _factCFS.create(sidx), _factCF.create_(sidx));
        }
    }

    private final Factory _f;

    private PolarisStore(Factory f, SIndex sidx, ChangeFetchScheduler cfs, ContentFetcher cf)
            throws SQLException
    {
        super(sidx, ImmutableMap.of(
                ChangeFetchScheduler.class, cfs,
                ContentFetcher.class, cf));
        _cfs = cfs;
        _mcss = f._factMCSS.create(sidx);
        _ccss = f._factCCSS.create(sidx);
        _cf = cf;
        _f = f;
    }

    public SubmissionScheduler<ContentChangeSubmitter> contentSubmitter()
    {
        return _ccss;
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

    @Override
    public void onPauseSync_()
    {
       _mcss.stop_();
       _ccss.stop_();
       _cfs.stop_();
       _f._cnsub.unsubscribe_(this);
    }

    @Override
    public void onResumeSync_()
    {
        _mcss.start_();
        _ccss.start_();
        _cfs.start_();
        _f._cnsub.subscribe_(this);
    }

    /**
     * Called after the Store is created.
     * If there are no OPM devices for this store, do nothing.
     */
    void postCreate_()
    {
        // start submitting local updates to polaris
        _mcss.start_();
        _ccss.start_();

        // start fetching updates from polaris
        _cfs.start_();

        // subscribe to change notifications
        _f._cnsub.subscribe_(this);

        _f._devices.afterAddingStore_(_sidx);

        // make sure collector is started if peers are online
        if (hasOnlinePotentialMemberDevices_()) {
            // we map online devices in the collector as OPM devices of a member store
            getOnlinePotentialMemberDevices_().keySet().forEach(this::notifyDeviceOnline_);
        }

        _f._pauseSync.addListener_(this);
    }

    /**
     * Pre-deletion trigger. Before we remove a Store from the map, mark the devices offline
     * for this store.
     */
    void preDelete_()
    {
        _f._pauseSync.removeListener_(this);

        // stop submitting local updates to polaris
        _mcss.stop_();
        _ccss.stop_();

        // stop fetching updates from polaris
        _cfs.stop_();

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
