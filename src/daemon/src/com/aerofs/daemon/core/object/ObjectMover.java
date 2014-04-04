package com.aerofs.daemon.core.object;

import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.phy.PhysicalOp;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.CID;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;

import javax.inject.Inject;

import static com.google.common.base.Preconditions.checkArgument;

public class ObjectMover
{
    private final DirectoryService _ds;
    private final VersionUpdater _vu;
    private final Expulsion _expulsion;

    @Inject
    public ObjectMover(VersionUpdater vu, DirectoryService ds, Expulsion expulsion)
    {
        _vu = vu;
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
        // The caller must guarantee the local existence of the object and its new parent
        OA oaOld = _ds.getOA_(soid);
        OA oaParent = _ds.getOA_(new SOID(soid.sidx(), oidParent));

        final ResolvedPath pOld = _ds.resolve_(oaOld);
        final ResolvedPath pParent = _ds.resolve_(oaParent);
        checkArgument(!pParent.isUnderOrEqual(pOld), "cannot move object under itself");

        _ds.setOAParentAndName_(oaOld, oaParent, name, t);

        if (updateVersion) {
            SOCKID k = new SOCKID(soid, CID.META);
            _vu.update_(k, t);
        }

        _expulsion.objectMoved_(pOld, soid, emigrate, op, t);
    }
}
