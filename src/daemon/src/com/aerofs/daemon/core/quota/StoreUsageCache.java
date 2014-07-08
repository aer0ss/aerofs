/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.quota;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryServiceAdapter;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOKID;
import com.google.common.collect.Maps;

import javax.inject.Inject;
import java.sql.SQLException;
import java.util.Map;

/**
 * An in-memory cache for per-store usage to reduce calls to
 * {@link com.aerofs.daemon.lib.db.IMetaDatabase#getBytesUsed_}, which may be expensive for
 * large stores. TODO (WW) profile and optimize the performance of that call.
 */
class StoreUsageCache
{
    private final DirectoryService _ds;
    private final Map<SIndex, Long> _cache = Maps.newHashMap();

    @Inject
    StoreUsageCache(DirectoryService ds)
    {
        _ds = ds;

        // invalidate the cache on any content change
        _ds.addListener_(new DirectoryServiceAdapter()
        {
            @Override
            public void objectContentCreated_(SOKID obj, Path path, Trans t) { invalidate_(obj); }

            @Override
            public void objectContentDeleted_(SOKID obj, Trans t) { invalidate_(obj); }

            @Override
            public void objectContentModified_(SOKID obj, Path path, Trans t) { invalidate_(obj); }

            private void invalidate_(SOKID obj)
            {
                _cache.remove(obj.sidx());
            }
        });
    }

    long getBytesUsed_(SIndex sidx)
            throws SQLException
    {
        Long used = _cache.get(sidx);
        if (used == null) {
            used = _ds.getBytesUsed_(sidx);
            _cache.put(sidx, used);
        }
        return used;
    }
}
