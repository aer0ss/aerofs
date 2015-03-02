/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.store.StoreHierarchy.StoreCreationListener;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.id.SIndex;

import javax.inject.Inject;
import java.sql.SQLException;
import java.util.concurrent.Callable;

/*
 * There should be one instance of this class.
 * Access to this class is protected by the core lock.
 */
public class Stores implements IStoreDeletionOperator, StoreCreationListener
{
    private final StoreHierarchy _sh;
    private final SIDMap _sm;
    private final Store.Factory _factStore;
    private final MapSIndex2Store _sidx2s;

    @Inject
    public Stores(
            StoreHierarchy sh,
            SIDMap sm,
            Store.Factory factStore,
            MapSIndex2Store sidx2s,
            StoreDeletionOperators sdo)
    {
        _sh = sh;
        _sm = sm;
        _factStore = factStore;
        _sidx2s = sidx2s;

        _sh.setListener_(this);
        sdo.addImmediate_(this);
    }

    public void init_() throws Exception
    {
        // IOException is in my base, killin all my lambdazzz
        for (SIndex sidx : _sh.getAll_()) { notifyAddition_(sidx); }
    }

    @Override
    public void storeAdded_(final SIndex sidx, Trans t)
            throws SQLException
    {
        notifyAddition_(sidx);

        registerRollbackHandler_(t, () -> {
            notifyDeletion_(sidx);
            return null;
        });
    }

    @Override
    public void deleteStore_(final SIndex sidx, Trans t) throws SQLException
    {
        // Grab a reference to the Store object before notifyDeletion_ removes in-memory references
        // to it
        Store s = _sidx2s.get_(sidx);

        notifyDeletion_(sidx);

        _sh.remove_(sidx, t);

        s.deletePersistentData_(t);

        registerRollbackHandler_(t, () -> {
            notifyAddition_(sidx);
            return null;
        });
    }

    /**
     * Create a Store instance and notify Devices accordingly. Record the Store
     * in the sidx2s map.
     */
    private void notifyAddition_(SIndex sidx) throws SQLException
    {
        _sm.add_(sidx);
        // this is the only place Store instances are created...
        Store s = _factStore.create_(sidx);
        _sidx2s.add_(s);

        s.postCreate_();
    }

    /**
     * Notify Devices and affected Store instances that this store is going to be deleted;
     * then remove it from the maps.
     */
    private void notifyDeletion_(SIndex sidx)
    {

        _sidx2s.get_(sidx).preDelete_();

        _sidx2s.delete_(sidx);
        _sm.delete_(sidx);
    }

    private void registerRollbackHandler_(Trans t, final Callable<Void> callable)
    {
        t.addListener_(new AbstractTransListener() {
            @Override
            public void aborted_()
            {
                try {
                    callable.call();
                } catch (Exception e) {
                    // we can't recover from the error
                    SystemUtil.fatal(e);
                }
            }
        });
    }
}