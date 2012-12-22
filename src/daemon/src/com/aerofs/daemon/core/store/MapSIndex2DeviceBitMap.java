/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.Map;

/**
 * Wrapper around the device mapping column of IStoreDatabase, with caching
 *
 * see {@link DeviceBitMap} for details
 */
public class MapSIndex2DeviceBitMap
{
    private final IStoreDatabase _sdb;
    private final Map<SIndex, DeviceBitMap> _s2d;

    @Inject
    public MapSIndex2DeviceBitMap(IStoreDatabase sdb)
    {
        _sdb = sdb;
        _s2d = Maps.newHashMap();
    }

    /**
     * Retrieve the mapping of devices to (sync status bitvector) index for this store
     *
     * @return DID<->index bidirectional map
     */
    public DeviceBitMap getDeviceMapping_(SIndex sidx) throws SQLException
    {
        DeviceBitMap dbm = _s2d.get(sidx);
        if (dbm == null) {
            dbm = new DeviceBitMap(_sdb.getDeviceMapping_(sidx));
            // add to cache
            _s2d.put(sidx, dbm);
        }
        return dbm;
    }

    /**
     * Register a new device for a given store
     *
     * @pre the DID must not be present in the map
     * @param did remote device identifier
     * @param t transaction (this method can only be called as part of a transaction)
     * @return index of the added device within the store
     */
    public int addDevice_(SIndex sidx, DID did, Trans t) throws SQLException
    {
        DeviceBitMap dbm = getDeviceMapping_(sidx);

        // make sure the cache remains consistent on trans failure
        final SIndex inval_sidx = sidx;
        t.addListener_(new AbstractTransListener()
        {
            @Override
            public void aborted_()
            {
                invalidateCache(inval_sidx);
            }
        });

        // update cache
        int idx = dbm.addDevice_(did);

        // commit change to DB
        _sdb.setDeviceMapping_(sidx, dbm.getBytes(), t);

        return idx;
    }

    /**
     * Invalidate cache for a given store
     */
    public void invalidateCache(SIndex sidx)
    {
        _s2d.remove(sidx);
    }
}
