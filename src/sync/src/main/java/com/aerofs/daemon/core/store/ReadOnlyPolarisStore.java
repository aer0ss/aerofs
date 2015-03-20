package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.net.device.Device;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.polaris.fetch.ChangeFetchScheduler;
import com.aerofs.daemon.core.polaris.fetch.ChangeNotificationSubscriber;
import com.aerofs.daemon.core.polaris.fetch.ContentFetcher;
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

public class ReadOnlyPolarisStore extends Store
{
    private final ChangeFetchScheduler _cfs;
    private final ContentFetcher _cf;

    public static class Factory implements Store.Factory
    {
        @Inject private Devices _devices;
        @Inject private ChangeNotificationSubscriber _cnsub;
        @Inject protected ChangeFetchScheduler.Factory _factCFS;
        @Inject protected ContentFetcher.Factory _factCF;
        @Inject private PauseSync _pauseSync;

        public Store create_(SIndex sidx) throws SQLException
        {
            return new ReadOnlyPolarisStore(this, sidx, _factCFS.create(sidx), _factCF.create_(sidx));
        }
    }

    private final Factory _f;

    protected ReadOnlyPolarisStore(Factory f, SIndex sidx, ChangeFetchScheduler cfs, ContentFetcher cf)
            throws SQLException
    {
        super(sidx, ImmutableMap.of(
                ChangeFetchScheduler.class, cfs,
                ContentFetcher.class, cf));
        _cfs = cfs;
        _cf = cf;
        _f = f;
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
       _cfs.stop_();
       _f._cnsub.unsubscribe_(this);
    }

    @Override
    public void onResumeSync_()
    {
        _cfs.start_();
        _f._cnsub.subscribe_(this);
    }

    @Override
    void postCreate_()
    {
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

    @Override
    void preDelete_()
    {
        _f._pauseSync.removeListener_(this);

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
