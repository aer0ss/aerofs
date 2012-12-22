package com.aerofs.daemon.core.multiplicity.singleuser.migration;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.lib.cfg.CfgBuildType;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.Collection;

/**
 * This class locates admitted objects by OID across all the stores.
 *
 * IMPORTANT invariant: The local device must maintain an invariant that among all the objects
 * sharing the same OID, at most one of them is admitted (the rest are expelled).
 */
class AdmittedObjectLocator
{
    private final IMetaDatabase _mdb;
    private final DirectoryService _ds;
    private final CfgBuildType _cfgBuildType;

    @Inject
    public AdmittedObjectLocator(IMetaDatabase mdb, DirectoryService ds, CfgBuildType cfgBuildType)
    {
        _mdb = mdb;
        _ds = ds;
        _cfgBuildType = cfgBuildType;
    }

    public OA locate_(OID oid, SIndex sidxExcluded, OA.Type typeExpected)
        throws SQLException
    {
        return locateImpl_(oid, _mdb.getSIndexes_(oid, sidxExcluded), typeExpected);
    }

    private OA locateImpl_(OID oid, Collection<SIndex> sidxs, OA.Type typeExpected)
            throws SQLException
    {
        OA oaFound = null;
        for (SIndex sidx : sidxs) {
            SOID soid = new SOID(sidx, oid);
            OA oa = _ds.getOA_(soid);
            assert oa.soid().oid().equals(oid);
            assert oa.type() == typeExpected;
            if (!oa.isExpelled()) {
                if (_cfgBuildType.isStaging()) {
                    assert oaFound == null;
                    oaFound = oa;
                } else {
                    oaFound = oa;
                    break;
                }
            }
        }

        return oaFound;
    }
}
