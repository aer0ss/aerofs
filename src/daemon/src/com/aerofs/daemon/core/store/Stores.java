/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.net.device.DevicePresence;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.id.SIndex;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/*
 * There should be one instance of this class.
 * Access to this class is protected by the core lock.
 */
public class Stores implements IStores, IStoreDeletionOperator
{
    protected IStoreDatabase _sdb;
    private SIDMap _sm;
    private MapSIndex2Store _sidx2s;
    private DevicePresence _dp;

    // cached hierarchy information
    private Map<SIndex, Set<SIndex>> _parents = Maps.newHashMap();
    private Map<SIndex, Set<SIndex>> _children = Maps.newHashMap();

    private static final Logger l = Loggers.getLogger(Stores.class);

    @Inject
    public void inject_(IStoreDatabase sdb, SIDMap sm, MapSIndex2Store sidx2s,
            DevicePresence dp, StoreDeletionOperators sdo)
    {
        _sdb = sdb;
        _sm = sm;
        _sidx2s = sidx2s;
        _dp = dp;

        sdo.add_(this);
    }

    @Override
    public void init_() throws SQLException, IOException
    {
        for (SIndex sidx : _sdb.getAll_()) notifyAddition_(sidx);
    }

    private final TransLocal<Set<SIndex>> _tlChanged = new TransLocal<Set<SIndex>>() {
        @Override
        protected Set<SIndex> initialValue(Trans t)
        {
            final Set<SIndex> set = Sets.newHashSet();
            t.addListener_(new AbstractTransListener() {
                @Override
                public void aborted_()
                {
                    // invalidate entries touched by transaction
                    for (SIndex sidx : set) {
                        _parents.remove(sidx);
                        _children.remove(sidx);
                    }
                }
            });
            return set;
        }
    };

    @Override
    public void addParent_(SIndex sidx, SIndex sidxParent, Trans t)
            throws SQLException
    {
        _sdb.assertExists_(sidx);
        _sdb.assertExists_(sidxParent);

        Set<SIndex> p = _parents.get(sidx);
        if (p != null) {
            _tlChanged.get(t).add(sidx);
            p.add(sidxParent);
        }

        Set<SIndex> c = _children.get(sidxParent);
        if (c != null) {
            _tlChanged.get(t).add(sidxParent);
            c.add(sidx);
        }

        _sdb.insertParent_(sidx, sidxParent, t);
    }

    @Override
    public void deleteParent_(SIndex sidx, SIndex sidxParent, Trans t)
            throws SQLException
    {
        _sdb.assertExists_(sidxParent);

        Set<SIndex> p = _parents.get(sidx);
        if (p != null) {
            _tlChanged.get(t).add(sidx);
            p.remove(sidxParent);
        }

        Set<SIndex> c = _children.get(sidxParent);
        if (c != null) {
            _tlChanged.get(t).add(sidxParent);
            c.remove(sidx);
        }

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
        Set<SIndex> p = _parents.get(sidx);
        if (p == null) {
            _sdb.assertExists_(sidx);
            p = _sdb.getParents_(sidx);
            _parents.put(sidx, p);
        }
        return Sets.newHashSet(p);
    }

    @Override
    public @Nonnull Set<SIndex> getChildren_(SIndex sidx) throws SQLException
    {
        Set<SIndex> c = _children.get(sidx);
        if (c == null) {
            _sdb.assertExists_(sidx);
            c = _sdb.getChildren_(sidx);
            _children.put(sidx, c);
        }
        return Sets.newHashSet(c);
    }

    @Override
    public @Nonnull Set<SIndex> getAll_() throws SQLException
    {
        return _sdb.getAll_();
    }

    @Override
    public SIndex getPhysicalRoot_(SIndex sidx) throws SQLException
    {
        // default implementation for multiuser system, overriden in SingleuserStores
        return sidx;
    }

    @Override
    public String getName_(SIndex sidx) throws SQLException
    {
        return _sdb.getName_(sidx);
    }

    @Override
    public void add_(final SIndex sidx, String name, Trans t)
            throws SQLException
    {
        Preconditions.checkState(!_parents.containsKey(sidx), sidx);
        Preconditions.checkState(!_children.containsKey(sidx), sidx);
        Preconditions.checkState(_sdb.getParents_(sidx).isEmpty());
        Preconditions.checkState(_sdb.getChildren_(sidx).isEmpty());

        _sdb.insert_(sidx, name, t);

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

        _parents.remove(sidx);
        _children.remove(sidx);

        // Grab a reference to the Store object before notifyDeletion_ removes in-memory references
        // to it
        Store s = _sidx2s.get_(sidx);

        notifyDeletion_(sidx);

        s.deletePersistentData_(t);

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

    /**
     * Create a Store instance and notify the DevicePresence accordingly. Record the Store
     * in the sidx2s map.
     */
    private void notifyAddition_(SIndex sidx) throws SQLException
    {
        _sm.add_(sidx);
        // this is the only place Store instances are created...
        Store s = _sidx2s.add_(sidx);

        _dp.afterAddingStore_(sidx);
        s.postCreate();
    }

    /**
     * Notify the DevicePresence and Store instances that this store is going to be deleted;
     * then remove it from the maps.
     */
    private void notifyDeletion_(SIndex sidx)
    {
        _dp.beforeDeletingStore_(sidx);

        _sidx2s.get_(sidx).preDelete();

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