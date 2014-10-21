package com.aerofs.daemon.core.multiplicity.singleuser.migration;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.lib.cfg.CfgAggressiveChecking;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import java.sql.SQLException;

/**
 * This class locates admitted objects by OID across all the stores.
 *
 * IMPORTANT invariant: The local device must maintain an invariant that among all the objects
 * sharing the same OID, at most one of them is admitted (the rest are expelled).
 */
class AdmittedObjectLocator
{
    private final StoreHierarchy _stores;
    private final DirectoryService _ds;
    private final CfgAggressiveChecking _cfgAggressiveChecking;

    @Inject
    public AdmittedObjectLocator(StoreHierarchy stores, DirectoryService ds,
            CfgAggressiveChecking cfgAggressiveChecking)
    {
        _ds = ds;
        _stores = stores;
        _cfgAggressiveChecking = cfgAggressiveChecking;
    }

    public OA locate_(OID oid, SIndex sidxExcluded, OA.Type typeExpected)
        throws SQLException
    {
        final boolean aggressiveChecking = _cfgAggressiveChecking.get();
        OA oaFound = null;
        // One might naively think that having a query per store would be slower than one big query
        // that goes over all stores. This is very much not the case (at least not given the current
        // state of the CoreSchema).
        // Looking up the set of SIndex for which an OA with a given OID exists requires a table
        // scan so it essentially takes time O(N) where N is the number of files across all stores
        // On the other hand the following loop takes time O(S log N) where S is the number of
        // admitted stores
        // Adding an index on the OID column would allow a single O(log N) query but it would also
        // make the DB significantly larger and modifications of the OA table noticeably slower...
        for (SIndex sidx : _stores.getAll_()) {
            if (sidx.equals(sidxExcluded)) continue;
            OA oa = _ds.getOANullable_(new SOID(sidx, oid));
            if (oa != null) {
                assert oaFound == null : oaFound + " " + oa;
                assert oa.type() == typeExpected : oa + " " + typeExpected;
                if (oa.isExpelled()) continue;
                oaFound = oa;
                if (!aggressiveChecking) break;
            }
        }
        return oaFound;
    }
}
