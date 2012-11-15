/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.sumu.multiuser;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.store.IStoreDeletionListener.StoreDeletionNotifier;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.core.store.StoresBase;
import com.aerofs.daemon.core.store.StoresBase.IStoresBaseOperator;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Path;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.SIndex;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;

public class MultiuserStores implements IStores, IStoresBaseOperator
{
    private StoresBase _base;
    private TransManager _tm;
    private StoreCreator _sc;

    private SIndex _root;

    @Inject
    public void inject_(IStoreDatabase sdb, TransManager tm, StoreCreator sc, SIDMap sm,
            MapSIndex2Store sidx2s, MapSIndex2DeviceBitMap sidx2dbm, DevicePresence dp,
            StoreDeletionNotifier sdn)
    {
        _base = new StoresBase(sdb, sm, sidx2s, sidx2dbm, dp, sdn, this);
        _tm = tm;
        _sc = sc;
    }

    @Override
    public void init_() throws SQLException, ExAlreadyExist, IOException
    {
        _base.init_();

        if (!_base.getAll_().isEmpty()) return;

        // no store exists. create the root store.
        Trans t = _tm.begin_();
        try {
            _sc.createRootStore_(t);
            t.commit_();
        } finally {
            t.end_();
        }

        // createRootStore_() above should indirectly call this.postAdd_() which sets _root.
        assert _root != null;
    }

    @Override
    public void add_(SIndex sidx, @Nonnull SIndex sidxParent, Trans t)
            throws SQLException
    {
        _base.add_(sidx, sidxParent, t);
    }

    @Override
    public void setParent_(SIndex sidx, SIndex sidxParent, Trans t)
            throws SQLException
    {
        _base.setParent_(sidx, sidxParent, t);
    }

    @Override
    public @Nonnull SIndex getParent_(SIndex sidx) throws SQLException
    {
        return _base.getParent_(sidx);
    }

    @Override
    public Set<SIndex> getChildren_(SIndex sidx) throws SQLException
    {
        return _base.getChildren_(sidx);
    }

    @Override
    public Set<SIndex> getAll_() throws SQLException
    {
        return _base.getAll_();
    }

    @Override
    public @Nonnull SIndex getRoot_(Path path)
    {
        assert _root != null;
        return _root;
    }

    @Override
    public boolean isRoot_(SIndex sidx)
    {
        assert _root != null;
        return sidx.equals(_root);
    }

    @Override
    public void postReset_()
    {
        _root = null;
        for (SIndex sidx : _base.getAll_()) {
            if (sidx.equals(_base.getParent_(sidx))) {
                assert _root == null;
                _root = sidx;
            }
        }
    }

    @Override
    public void postAdd_(SIndex sidx, Trans t)
    {
        if (!_base.getParent_(sidx).equals(sidx)) return;

        assert _root == null;
        _root = sidx;

        t.addListener_(new AbstractTransListener()
        {
            @Override
            public void aborted_()
            {
                _root = null;
            }
        });
    }

    @Override
    public void postDelete_(SIndex sidx)
    {
        assert !sidx.equals(_root);
    }
}
