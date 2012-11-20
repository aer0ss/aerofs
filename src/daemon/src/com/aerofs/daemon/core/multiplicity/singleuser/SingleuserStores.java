/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.core.store.Stores;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.SIndex;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;

/**
 * See class-level comments in IStores for details.
 */
public class SingleuserStores extends Stores
{
    private TransManager _tm;
    private StoreCreator _sc;
    private SIndex _root;

    @Inject
    public void inject_(TransManager tm, StoreCreator sc)
    {
        _tm = tm;
        _sc = sc;
    }

    @Override
    public void init_() throws SQLException, IOException
    {
        super.init_();

        if (!_sdb.hasAny_()) createRootStore_();

        setRootStore_();
    }

    private void createRootStore_() throws SQLException, IOException
    {
        Trans t = _tm.begin_();
        try {
            _sc.createRootStore_(t);
            t.commit_();
        } catch (ExAlreadyExist e) {
            SystemUtil.fatal(e);
        } finally {
            t.end_();
        }
    }

    private void setRootStore_() throws SQLException
    {
        assert _root == null;
        for (SIndex sidx : super.getAll_()) {
            if (super.getParents_(sidx).isEmpty()) {
                assert _root == null;
                _root = sidx;
            }
        }
        assert _root != null;
    }

    @Override
    public @Nonnull Set<SIndex> getParents_(SIndex sidx) throws SQLException
    {
        Set<SIndex> ret = super.getParents_(sidx);
        assert ret.size() <= 1;
        return ret;
    }

    /**
     * @return the parent of the given store. For single-user systems, a non-root store has and only
     * has one parent.
     *
     * @pre The store is not a root store
     */
    public @Nonnull SIndex getParent_(SIndex sidx) throws SQLException
    {
        Collection<SIndex> ret = getParents_(sidx);
        assert ret.size() == 1;
        return ret.iterator().next();
    }

    public @Nonnull SIndex getRoot_()
    {
        assert _root != null;
        return _root;
    }

    @Override
    public boolean isRoot_(SIndex sidx)
    {
        return _root.equals(sidx);
    }
}
