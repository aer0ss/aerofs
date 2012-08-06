package com.aerofs.daemon.core.object;

import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.phy.PhysicalOp;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;

import javax.inject.Inject;

public class ObjectMover
{
    private DirectoryService _ds;
    private ComMonitor _cm;
    private Expulsion _expulsion;

    @Inject
    public void inject_(ComMonitor cm, DirectoryService ds, Expulsion expulsion)
    {
        _cm = cm;
        _ds = ds;
        _expulsion = expulsion;
    }

    /**
      * TODO avoid moving a directory to a subdir of itself, or moving root dir
      * @param emigrate whether the move results from emigration
      */
    public void moveInSameStore_(final SOID soid, OID oidParent, String name, PhysicalOp op,
            boolean emigrate, boolean updateVersion, Trans t)
            throws Exception
    {
        OA oaOld = _ds.getOANullable_(soid);
        OA oaParent = _ds.getOANullable_(new SOID(soid.sidx(), oidParent));

        // The caller must guarantee the local existence of the object and its new parent
        // N.B.
        //  1) getOA_(...) would suffice for this assertion, but we want more information logged.
        //  2) we print the OA using getAliasedOANullable instead of variables
        //     because the result is the same if neither are aliased, and the results are more
        //     informative if either is aliased
        assert oaOld != null && oaParent != null : "soid " + soid + " oaAliased "
                        + _ds.getAliasedOANullable_(soid) + " oidP " + oidParent
                        + " oaPAliased "
                        + _ds.getAliasedOANullable_(new SOID(soid.sidx(), oidParent));

        final Path pOld = _ds.resolve_(oaOld);

        _ds.setOAParentAndName_(oaOld, oaParent, name, t);

        if (updateVersion) {
            SOCKID k = new SOCKID(soid, CID.META);
            _cm.atomicWrite_(k, t);
        }

        _expulsion.objectMoved_(emigrate, op, soid, pOld, t);
    }
}
