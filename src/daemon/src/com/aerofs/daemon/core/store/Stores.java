package com.aerofs.daemon.core.store;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.IStoreDatabase.StoreRow;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class Stores implements IStores
{
    private IStoreDatabase _sdb;
    private TransManager _tm;
    private StoreCreator _sc;
    private SIDMap _sm;
    private MapSIndex2Store _sidx2s;
    private DevicePresence _dp;

    // map: sidx -> parent sidx for locally present stores
    private final Map<SIndex, SIndex> _s2parent = Maps.newHashMap();

    private SIndex _root;

    /**
     * Straightforward implementation of IDiDBiMap
     */
    private static class DIDBiMap implements IDIDBiMap {
        private List<DID> _l;
        private Map<DID, Integer> _m;

        /**
         * Create and fill BiMap from concatenated DIDs
         * @param dids
         */
        public DIDBiMap(byte[] dids) {
            _l = new ArrayList<DID>();
            _m = new HashMap<DID, Integer>();

            if (dids != null) {
                assert dids.length % DID.LENGTH == 0;
                for (int i = 0; i + DID.LENGTH <= dids.length; i += DID.LENGTH) {
                    DID did = new DID(Arrays.copyOfRange(dids, i , i + DID.LENGTH));
                    _l.add(did);
                    _m.put(did, i);
                }
            }
        }

        /**
         * Add DID to the BiMap
         * @param did
         * @return false if the DID was already present in the BiMap
         */
        public int addDevice(DID did) {
            int idx = _l.size();
            _m.put(did, idx);
            _l.add(did);
            return idx;
        }

        /**
         * @return Concatenated byte array representation suitable for DB storage
         */
        public byte[] getBytes() {
            byte[] d = new byte[_l.size() * DID.LENGTH];
            int i = 0;
            for (DID did : _l) {
                System.arraycopy(did.getBytes(), 0, d, i++ * DID.LENGTH, DID.LENGTH);
            }
            return d;
        }

        @Override
        public int size() {
            return _l.size();
        }

        @Override
        public Integer get(DID did) {
            return _m.get(did);
        }

        @Override
        public DID get(int index) {
            return _l.get(index);
        }

        @Override
        public Iterator<DID> iterator() {
            return _l.iterator();
        }
    }
    // DID<->index mapping cache
    private Map<SIndex, DIDBiMap> _s2d;

    @Inject
    public void inject_(IStoreDatabase sdb, TransManager tm, StoreCreator sc, SIDMap sm,
            MapSIndex2Store sidx2s, DevicePresence dp)
    {
        _sdb = sdb;
        _tm = tm;
        _sc = sc;
        _sm = sm;
        _sidx2s = sidx2s;
        _dp = dp;
        _s2d = new HashMap<SIndex, DIDBiMap>();
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
        _s2d.remove(sidx);
        _sdb.delete_(sidx, t);

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
    public SIndex getRoot_()
    {
        return _root;
    }

    @Override
    public SIndex getParent_(SIndex sidx) throws SQLException
    {
        assert _s2parent.containsKey(sidx);
        return _s2parent.get(sidx);
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


    /**
     * Simple bidirectional DID<->index mapping for a store
     */
    public static interface IDIDBiMap extends Iterable<DID> {
        int size();
        Integer get(DID did);
        DID get(int index);
    }

    /**
     * Retrieve the mapping of devices to (sync status bitvector) index for this store
     * @param sidx Store index
     * @return DID<->index bidirectional map
     * @throws SQLException
     */
    public IDIDBiMap getDeviceMapping_(SIndex sidx) throws SQLException {
        DIDBiMap dbm = _s2d.get(sidx);
        if (dbm == null) {
            dbm = new DIDBiMap(_sdb.getDeviceMapping_(sidx));
            // add to cache
            _s2d.put(sidx, dbm);
        }
        return dbm;
    }

    /**
     * Register a new device for a given store
     *
     * All DIDBiMap objects for the given store will reflect the change
     *
     * @param sidx store index
     * @param did remote device identifier
     * @param t transaction (this method can only be called as part of a transaction)
     * @return index of the added device within the store
     */
    public int addDevice_(SIndex sidx, DID did, Trans t) throws SQLException {
        DIDBiMap dbm = (DIDBiMap)getDeviceMapping_(sidx);

        Integer idx = dbm.get(did);
        if (idx != null)
            return idx.intValue();

        // update cache
        idx = dbm.addDevice(did);

        // commit change to DB
        _sdb.setDeviceMapping_(sidx, dbm.getBytes());

        return idx;
    }
}
