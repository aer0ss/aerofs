/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.lib.Path;
import com.aerofs.ids.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Sets;

import javax.inject.Inject;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;

/**
 * This class recursively computes all the stores under a given object
 */
public class DescendantStores
{
    final private DirectoryService _ds;
    final private StoreHierarchy _sh;
    final private IMapSIndex2SID _sidx2sid;

    @Inject
    public DescendantStores(DirectoryService ds, StoreHierarchy sh, IMapSIndex2SID sidx2sid)
    {
        _ds = ds;
        _sh = sh;
        _sidx2sid = sidx2sid;
    }

    /**
     * @return a set of all stores (strictly) under a given SOID (recursively)
     */
    public Set<SIndex> getDescendantStores_(SOID soid) throws SQLException
    {
        Set<SIndex> set = Sets.newHashSet();

        SIndex sidx = soid.sidx();
        Path path = _ds.resolve_(soid);

        // among immediate children of the given store, find those who are under the given path
        Collection<SIndex> children = _sh.getChildren_(sidx);
        for (SIndex csidx : children) {
            if (csidx.equals(sidx)) continue;

            SID csid = _sidx2sid.get_(csidx);
            Path cpath = _ds.resolve_(new SOID(sidx, SID.storeSID2anchorOID(csid)));
            if (cpath.isStrictlyUnder(path)) {
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
        for (SIndex csidx : _sh.getChildren_(sidx)) addChildren_(csidx, set);
    }
}
