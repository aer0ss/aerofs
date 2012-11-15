/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.IStoreDatabase.StoreRow;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * This class provide base functions for MultiuserStores and SingleuserStores. The store-to-parent
 * mapping maintained in this class is not useful for multi-user systems, but kept in this base
 * class for simplicity in implementation.
 */
public class StoresBase implements IStoreDeletionListener
{
    public static interface IStoresBaseOperator
    {
        /**
         * This method is called after the StoresBase class is reset. See reset_().
         */
        void postReset_();

        /**
         * This method is called after the store is added and all the data structures in related
         * classes have been updated.
         */
        void postAdd_(SIndex sidx, Trans t);

        /**
         * This method is called after the store is deleted and all the data structures in related
         * classes have been updated.
         */
        void postDelete_(SIndex sidx);
    }

    private IStoreDatabase _sdb;
    private SIDMap _sm;
    private MapSIndex2Store _sidx2s;
    private MapSIndex2DeviceBitMap _sidx2dbm;
    private DevicePresence _dp;

    // map: sidx -> parent sidx for locally present stores
    private final Map<SIndex, SIndex> _s2parent = Maps.newHashMap();

    private static final Logger l = Util.l(StoresBase.class);
    private IStoresBaseOperator _operator;

    public StoresBase(IStoreDatabase sdb, SIDMap sm, MapSIndex2Store sidx2s,
            MapSIndex2DeviceBitMap sidx2dbm, DevicePresence dp, StoreDeletionNotifier sdn,
            IStoresBaseOperator operator)
    {
        _sdb = sdb;
        _sm = sm;
        _sidx2s = sidx2s;
        _sidx2dbm = sidx2dbm;
        _dp = dp;
        _operator = operator;

        sdn.addListener_(this);
    }

    public void init_() throws SQLException, ExAlreadyExist, IOException
    {
        reset_();

        for (SIndex sidx : _s2parent.keySet()) notifyAddition_(sidx);
    }

    /**
     * reset the class's data structure but don't notify other classes about store addition or
     * deletion.
     */
    private void reset_() throws SQLException
    {
        _s2parent.clear();

        for (StoreRow sr : _sdb.getAll_()) {
            Util.verify(_s2parent.put(sr._sidx, sr._sidxParent) == null);
        }

        _operator.postReset_();
    }

    public void add_(final SIndex sidx, @Nonnull SIndex sidxParent, Trans t)
            throws SQLException
    {
        _sdb.add_(sidx, sidxParent, t);

        Util.verify(_s2parent.put(sidx, sidxParent) == null);
        notifyAddition_(sidx);

        registerRollbackHandler_(t, new Callable<Void>()
        {
            @Override
            public Void call()
                    throws SQLException
            {
                notifyDeletion_(sidx);
                reset_();
                return null;
            }
        });

        _operator.postAdd_(sidx, t);
    }

    @Override
    public void onStoreDeletion_(final SIndex sidx, Trans t) throws SQLException
    {
        // Grab a reference to the Store object before notifyDeletion_ removes in-memory references
        // to it
        Store s = _sidx2s.get_(sidx);

        notifyDeletion_(sidx);
        Util.verify(_s2parent.remove(sidx) != null);

        s.deletePersistentData_(t);

        // TODO (MJ) this is an odd place to invalidate the cache for sidx2dbm, especially since
        // it's the only reference to that class in this class. The only reason for this coupling
        // is that the DeviceBitMap is in the StoreDatabase. A cleaner/less coupled design would
        // use a separate table for the DeviceBitMaps, from the StoreDatabase, and then
        // MapSIndex2DeviceBitMap would "own" that database.
        _sidx2dbm.invalidateCache(sidx);
        // The following deletes the parent and DeviceBitMap for a store.
        _sdb.delete_(sidx, t);

        l.debug("Store removed from parent: " + sidx);

        registerRollbackHandler_(t, new Callable<Void>() {
            @Override
            public Void call() throws SQLException
            {
                reset_();
                notifyAddition_(sidx);
                return null;
            }
        });

        _operator.postDelete_(sidx);
    }

    private void notifyAddition_(SIndex sidx) throws SQLException
    {
        _sm.add_(sidx);
        _sidx2s.add_(sidx);
        _dp.storeAdded_(sidx);
    }

    private void notifyDeletion_(SIndex sidx)
    {
        _dp.beforeDeletingStore_(sidx);
        _sidx2s.delete_(sidx);
        _sm.delete_(sidx);
    }

    public void setParent_(SIndex sidx, SIndex sidxParent, Trans t)
            throws SQLException
    {
        _sdb.setParent_(sidx, sidxParent, t);
        Util.verify(_s2parent.put(sidx, sidxParent) != null);

        registerRollbackHandler_(t, new Callable<Void>()
        {
            @Override
            public Void call()
                    throws SQLException
            {
                reset_();
                return null;
            }
        });
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

    public @Nonnull SIndex getParent_(SIndex sidx)
    {
        // Parent must contain key sidx
        SIndex parent = _s2parent.get(sidx);
        assert parent != null : sidx;
        return parent;
    }

    public Set<SIndex> getChildren_(SIndex sidx)
    {
        assert _s2parent.containsKey(sidx);
        Set<SIndex> children = Sets.newTreeSet();
        for (Entry<SIndex, SIndex> en : _s2parent.entrySet()) {
            if (en.getValue().equals(sidx)) children.add(en.getKey());
        }
        return children;
    }

    public Set<SIndex> getAll_()
    {
        return Collections.unmodifiableSet(_s2parent.keySet());
    }
}
