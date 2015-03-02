/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * This interface provide a high-level accessor to IStoreDatabase. Clients should use this interface
 * instead of referring to IStoreDatabase directly.
 *
 * General model of store hierarchy:
 *
 *  o Each store has zero or one or more parents.
 *  o Store S is a parent of store T iff. S contains an admitted Anchor object referring to T.
 *  o Root stores have zero parents.
 *
 * Additional constrains in single-user systems (See SingleuserStoreHierarchy):
 *
 *  o There is a single root store whose SID is derived from the user ID.
 *  o Each non-root store has one and only one parent store. This is maintained by the migration
 *    system.
 *
 * Also see AbstractPathResolver for path hierarchy, which is related to but different from store
 * hierarchy.
 */
public class StoreHierarchy
{
    private static final Logger l = Loggers.getLogger(StoreHierarchy.class);

    private final IStoreDatabase _sdb;

    interface StoreCreationListener
    {
        void storeAdded_(SIndex sidx, Trans t) throws SQLException;
    }

    private StoreCreationListener _listener;

    // cached hierarchy information
    private final Map<SIndex, Set<SIndex>> _parents = Maps.newHashMap();
    private final Map<SIndex, Set<SIndex>> _children = Maps.newHashMap();

    @Inject
    public StoreHierarchy(IStoreDatabase sdb)
    {
        _sdb = sdb;
    }

    void setListener_(StoreCreationListener listener)
    {
        _listener = listener;
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

    public boolean isRoot_(SIndex sidx) throws SQLException
    {
        return getParents_(sidx).isEmpty();
    }

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

    public @Nonnull Set<SIndex> getAll_() throws SQLException
    {
        return _sdb.getAll_();
    }

    public SIndex getPhysicalRoot_(SIndex sidx) throws SQLException
    {
        // default implementation for multiuser system, overriden in SingleuserStores
        return sidx;
    }

    public String getName_(SIndex sidx) throws SQLException
    {
        return _sdb.getName_(sidx);
    }

    void add_(final SIndex sidx, String name, boolean usePolaris, Trans t)
            throws SQLException
    {
        checkState(!_parents.containsKey(sidx), sidx);
        checkState(!_children.containsKey(sidx), sidx);
        checkState(_sdb.getParents_(sidx).isEmpty());
        checkState(_sdb.getChildren_(sidx).isEmpty());

        _sdb.insert_(sidx, name, usePolaris, t);

        _listener.storeAdded_(sidx, t);
    }

    void remove_(final SIndex sidx, Trans t) throws SQLException
    {
        checkState(_sdb.getChildren_(sidx).isEmpty());

        _parents.remove(sidx);
        _children.remove(sidx);

        // The following deletes the parent and DeviceBitMap for a store.
        _sdb.delete_(sidx, t);

        l.debug("store deleted: {}", sidx);
    }
}
