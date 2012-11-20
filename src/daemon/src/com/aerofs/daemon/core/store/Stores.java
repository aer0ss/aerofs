/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SIndex;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.Callable;

public class Stores implements IStores, IStoreDeletionOperator
{
    protected IStoreDatabase _sdb;
    private SIDMap _sm;
    private MapSIndex2Store _sidx2s;
    private MapSIndex2DeviceBitMap _sidx2dbm;
    private DevicePresence _dp;

    private static final Logger l = Util.l(Stores.class);

    @Inject
    public void inject_(IStoreDatabase sdb, SIDMap sm, MapSIndex2Store sidx2s,
            MapSIndex2DeviceBitMap sidx2dbm, DevicePresence dp, StoreDeletionOperators sdo)
    {
        _sdb = sdb;
        _sm = sm;
        _sidx2s = sidx2s;
        _sidx2dbm = sidx2dbm;
        _dp = dp;

        sdo.add_(this);
    }

    @Override
    public void init_() throws SQLException, IOException
    {
        for (SIndex sidx : _sdb.getAll_()) notifyAddition_(sidx);
    }

    @Override
    public void addParent_(SIndex sidx, SIndex sidxParent, Trans t)
            throws SQLException
    {
        _sdb.assertExists_(sidx);
        _sdb.assertExists_(sidxParent);
        _sdb.addParent_(sidx, sidxParent, t);
    }

    @Override
    public void deleteParent_(SIndex sidx, SIndex sidxParent, Trans t)
            throws SQLException
    {
        _sdb.assertExists_(sidxParent);
        _sdb.deleteParent_(sidx, sidxParent, t);
    }

    @Override
    public boolean isRoot_(SIndex sidx) throws SQLException
    {
        return getParents_(sidx).isEmpty();
    }

    @Override
    public @Nonnull Set<SIndex> getParents_(SIndex sidx) throws SQLException
    {
        _sdb.assertExists_(sidx);
        return _sdb.getParents_(sidx);
    }

    @Override
    public @Nonnull Set<SIndex> getChildren_(SIndex sidx) throws SQLException
    {
        _sdb.assertExists_(sidx);
        return _sdb.getChildren_(sidx);
    }

    @Override
    public @Nonnull Set<SIndex> getAll_() throws SQLException
    {
        return _sdb.getAll_();
    }

    @Override
    public void add_(final SIndex sidx, Trans t)
            throws SQLException
    {
        assert _sdb.getParents_(sidx).isEmpty();
        assert _sdb.getChildren_(sidx).isEmpty();

        _sdb.add_(sidx, t);

        notifyAddition_(sidx);

        registerRollbackHandler_(t, new Callable<Void>()
        {
            @Override
            public Void call()
                    throws SQLException
            {
                notifyDeletion_(sidx);
                return null;
            }
        });
    }

    @Override
    public void deleteStore_(final SIndex sidx, Trans t) throws SQLException
    {
        assert _sdb.getChildren_(sidx).isEmpty();

        // Grab a reference to the Store object before notifyDeletion_ removes in-memory references
        // to it
        Store s = _sidx2s.get_(sidx);

        notifyDeletion_(sidx);

        s.deletePersistentData_(t);

        // TODO (MJ) this is an odd place to invalidate the cache for sidx2dbm, especially since
        // it's the only reference to that class in this class. The only reason for this coupling
        // is that the DeviceBitMap is in the StoreDatabase. A cleaner/less coupled design would
        // use a separate table for the DeviceBitMaps, from the StoreDatabase, and then
        // MapSIndex2DeviceBitMap would "own" that database.
        _sidx2dbm.invalidateCache(sidx);
        // The following deletes the parent and DeviceBitMap for a store.
        _sdb.delete_(sidx, t);

        l.debug("store deleted: " + sidx);

        registerRollbackHandler_(t, new Callable<Void>() {
            @Override
            public Void call() throws SQLException
            {
                notifyAddition_(sidx);
                return null;
            }
        });
    }

    private void notifyAddition_(SIndex sidx) throws SQLException
    {
        _sm.add_(sidx);
        _sidx2s.add_(sidx);
        _dp.afterAddingStore_(sidx);
    }

    private void notifyDeletion_(SIndex sidx)
    {
        _dp.beforeDeletingStore_(sidx);
        _sidx2s.delete_(sidx);
        _sm.delete_(sidx);
    }

    private void registerRollbackHandler_(Trans t, final Callable<Void> callable)
    {
        t.addListener_(new AbstractTransListener()
        {
            @Override
            public void aborted_()
            {
                try {
                    callable.call();
                } catch (Exception e) {
                    // we can't recover from the erorr
                    SystemUtil.fatal(e);
                }
            }
        });
    }
}
