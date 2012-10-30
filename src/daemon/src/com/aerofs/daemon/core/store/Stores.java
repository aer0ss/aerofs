package com.aerofs.daemon.core.store;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.IStoreDatabase.StoreRow;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.log4j.Logger;

public class Stores implements IStores
{
    private IStoreDatabase _sdb;
    private TransManager _tm;
    private StoreCreator _sc;
    private SIDMap _sm;
    private MapSIndex2Store _sidx2s;
    private IMapSIndex2SID _sidx2sid;
    private MapSIndex2DeviceBitMap _sidx2dbm;
    private DirectoryService _ds;
    private DevicePresence _dp;

    // map: sidx -> parent sidx for locally present stores
    private final Map<SIndex, SIndex> _s2parent = Maps.newHashMap();

    private SIndex _root;

    private static final Logger l = Util.l(Stores.class);

    @Inject
    public void inject_(IStoreDatabase sdb, TransManager tm, StoreCreator sc, SIDMap sm,
            MapSIndex2Store sidx2s, IMapSIndex2SID sidx2sid, MapSIndex2DeviceBitMap sidx2dbm,
            DirectoryService ds, DevicePresence dp)
    {
        _sdb = sdb;
        _tm = tm;
        _sc = sc;
        _sm = sm;
        _sidx2s = sidx2s;
        _sidx2sid = sidx2sid;
        _sidx2dbm = sidx2dbm;
        _ds = ds;
        _dp = dp;
    }

    public void init_() throws SQLException, ExAlreadyExist, IOException
    {
        populate_();

        // run the following tasks in init_() so when exceptions happen, the system can fail fast on
        // startup.
        if (_root != null) {
            for (SIndex sidx : _s2parent.keySet()) postAdd_(sidx);

        } else {
            assert _s2parent.isEmpty();
            Trans t = _tm.begin_();
            try {
                _root = _sc.createRootStore_(t);
                t.commit_();
            } finally {
                t.end_();
            }
        }
    }

    private void populate_() throws SQLException
    {
        _s2parent.clear();

        _root = null;
        for (StoreRow sr : _sdb.getAll_()) {
            Util.verify(_s2parent.put(sr._sidx, sr._sidxParent) == null);
            if (sr._sidx.equals(sr._sidxParent)) {
                assert _root == null;
                _root = sr._sidx;
            }
        }
    }

    @Override
    public void add_(final SIndex sidx, @Nonnull SIndex sidxParent, Trans t)
            throws SQLException
    {
        _sdb.add_(sidx, sidxParent, t);
        Util.verify(_s2parent.put(sidx, sidxParent) == null);
        postAdd_(sidx);

        registerRollbackHandler_(t, new Callable<Void>() {
            @Override
            public Void call() throws SQLException
            {
                preDelete_(sidx);
                populate_();
                return null;
            }
        });
    }

    @Override
    public void delete_(final SIndex sidx, Trans t) throws SQLException
    {
        preDelete_(sidx);
        Util.verify(_s2parent.remove(sidx) != null);
        _sidx2dbm.invalidateCache(sidx);

        _sdb.delete_(sidx, t);
        l.debug("Store removed from parent: " + sidx);

        registerRollbackHandler_(t, new Callable<Void>() {
            @Override
            public Void call() throws SQLException
            {
                populate_();
                postAdd_(sidx);
                return null;
            }
        });
    }

    private void postAdd_(SIndex sidx) throws SQLException
    {
        _sm.add_(sidx);
        _sidx2s.add_(sidx);
        _dp.storeAdded_(sidx);
    }

    private void preDelete_(SIndex sidx)
    {
        _dp.beforeDeletingStore_(sidx);
        _sidx2s.delete_(sidx);
        _sm.delete_(sidx);
    }

    @Override
    public void setParent_(SIndex sidx, SIndex sidxParent, Trans t)
            throws SQLException
    {
        _sdb.setParent_(sidx, sidxParent, t);
        Util.verify(_s2parent.put(sidx, sidxParent) != null);

        registerRollbackHandler_(t, new Callable<Void>() {
            @Override
            public Void call() throws SQLException
            {
                populate_();
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
                    Util.fatal(e);
                }
            }
        });
    }

    @Override
    public @Nonnull SIndex getRoot_()
    {
        assert _root != null;
        return _root;
    }

    @Override
    public @Nonnull SIndex getParent_(SIndex sidx) throws SQLException
    {
        // Parent must contain key sidx
        SIndex parent = _s2parent.get(sidx);
        assert parent != null : sidx;
        return parent;
    }

    @Override
    public Set<SIndex> getChildren_(SIndex sidx) throws SQLException
    {
        assert _s2parent.containsKey(sidx);
        Set<SIndex> children = Sets.newTreeSet();
        for (Entry<SIndex, SIndex> en : _s2parent.entrySet()) {
            if (en.getValue().equals(sidx)) children.add(en.getKey());
        }
        return children;
    }

    @Override
    public Set<SIndex> getAll_() throws SQLException
    {
        return Collections.unmodifiableSet(_s2parent.keySet());
    }

    @Override
    public Set<SIndex> getDescendants_(SOID soid) throws SQLException
    {
        Set<SIndex> set = Sets.newHashSet();

        SIndex sidx = soid.sidx();
        Path path = _ds.resolve_(soid);

        // among immediate children of the given store, find those who are under the given path
        Set<SIndex> children = getChildren_(sidx);
        for (SIndex csidx : children) {
            if (csidx == sidx) continue;

            SID csid = _sidx2sid.get_(csidx);
            Path cpath = _ds.resolve_(new SOID(sidx, SID.storeSID2anchorOID(csid)));
            if (cpath.isUnder(path)) {
                // recursively add child stores to result set
                addChildren_(csidx, set);
            }
        }

        return set;
    }

    private void addChildren_(SIndex sidx, Set<SIndex> set) throws SQLException
    {
        if (set.contains(sidx)) return;
        set.add(sidx);
        for (SIndex csidx : getChildren_(sidx)) addChildren_(csidx, set);
    }
}
