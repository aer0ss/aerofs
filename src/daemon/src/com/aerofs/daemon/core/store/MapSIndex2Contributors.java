package com.aerofs.daemon.core.store;

import com.aerofs.ids.DID;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.IStoreContributorsDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * In-memory cache for {@link com.aerofs.daemon.lib.db.IStoreContributorsDatabase}
 *
 * The cache is write-through and invalidated when a transaction affecting the db is aborted.
 */
public class MapSIndex2Contributors
{
    private final IStoreContributorsDatabase _scdb;

    private final Map<SIndex, Set<DID>> _contrib = Maps.newHashMap();

    @Inject
    public MapSIndex2Contributors(IStoreContributorsDatabase scdb)
    {
        _scdb = scdb;
    }

    public Set<DID> getContributors_(SIndex sidx) throws SQLException
    {
        return Collections.unmodifiableSet(getContributorsImpl_(sidx));
    }

    private Set<DID> getContributorsImpl_(SIndex sidx) throws SQLException
    {
        Set<DID> dids = _contrib.get(sidx);
        if (dids == null) {
            dids = _scdb.getContributors_(sidx);
            _contrib.put(sidx, dids);
        }
        return dids;
    }

    TransLocal<Set<SIndex>> _tlAdded = new TransLocal<Set<SIndex>>() {
        @Override
        protected Set<SIndex> initialValue(Trans t)
        {
            final Set<SIndex> set = Sets.newHashSet();
            t.addListener_(new AbstractTransListener() {
                @Override
                public void aborted_()
                {
                    for (SIndex sidx : set) _contrib.remove(sidx);
                }
            });
            return set;
        }
    };

    public void addContributor_(SIndex sidx, DID did, Trans t) throws SQLException
    {
        Set<DID> dids = getContributorsImpl_(sidx);
        if (dids.add(did)) {
            _scdb.addContributor_(sidx, did, t);
            _tlAdded.get(t).add(sidx);
        }
    }
}
