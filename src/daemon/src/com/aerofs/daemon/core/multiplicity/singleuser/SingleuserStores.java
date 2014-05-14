/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.core.store.Stores;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.SystemUtil;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.cfg.CfgRootSID;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Iterables;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * See class-level comments in IStores for details.
 */
public class SingleuserStores extends Stores
{
    private TransManager _tm;
    private StoreCreator _sc;
    private IMapSID2SIndex _sid2sidx;
    private CfgRootSID _cfgRootSID;
    private SIndex _userRoot;

    @Inject
    public void inject_(TransManager tm, StoreCreator sc, IMapSID2SIndex sid2sidx,
            CfgRootSID cfgRootSID)
    {
        _tm = tm;
        _sc = sc;
        _sid2sidx = sid2sidx;
        _cfgRootSID = cfgRootSID;
    }

    @Override
    public void init_() throws SQLException, IOException
    {
        super.init_();

        if (!_sdb.hasAny_()) createRootStore_();

        setUserRootStore_();
    }

    private void createRootStore_() throws SQLException, IOException
    {
        Trans t = _tm.begin_();
        try {
            _sc.createRootStore_(_cfgRootSID.get(), "", t);
            t.commit_();
        } catch (ExAlreadyExist e) {
            SystemUtil.fatal(e);
        } finally {
            t.end_();
        }
    }

    private void setUserRootStore_() throws SQLException
    {
        assert _userRoot == null;
        _userRoot = _sid2sidx.get_(_cfgRootSID.get());
    }

    @Override
    public @Nonnull Set<SIndex> getParents_(SIndex sidx) throws SQLException
    {
        Set<SIndex> ret = super.getParents_(sidx);
        assert ret.size() <= 1;
        return ret;
    }

    @Override
    public SIndex getPhysicalRoot_(SIndex sidx) throws SQLException
    {
        Set<SIndex> parents = getParents_(sidx);
        checkArgument(parents.size() <= 1, "%s %s", sidx, parents);
        return parents.isEmpty() ? sidx : getPhysicalRoot_(Iterables.getOnlyElement(parents));
    }

    /**
     * @return the parent of the given store. For single-user systems, a non-root store has exactly
     * one parent.
     *
     * @pre The store is not a root store
     */
    public @Nonnull SIndex getParent_(SIndex sidx) throws SQLException
    {
        Collection<SIndex> ret = getParents_(sidx);
        checkArgument(ret.size() == 1);
        return ret.iterator().next();
    }

    public @Nonnull SIndex getUserRoot_()
    {
        checkState(_userRoot != null);
        return _userRoot;
    }
}
