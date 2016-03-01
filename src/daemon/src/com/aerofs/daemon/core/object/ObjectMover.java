package com.aerofs.daemon.core.object;

import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.phy.PhysicalOp;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.CID;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;

import javax.inject.Inject;

import static com.google.common.base.Preconditions.checkArgument;

public class ObjectMover
{
    private DirectoryService _ds;
    private VersionUpdater _vu;
    private Expulsion _expulsion;

    @Inject
    public void inject_(VersionUpdater vu, DirectoryService ds, Expulsion expulsion)
    {
        _vu = vu;
        _ds = ds;
        _expulsion = expulsion;
    }

    /**
     *
     */
    public void moveInSameStore_(final SOID soid, OID oidParent, String name, PhysicalOp op,
            boolean updateVersion, Trans t)
            throws Exception
    {
        checkArgument(!soid.oid().isRoot());
        checkArgument(!soid.oid().isTrash());

        // The caller must guarantee the local existence of the object and its new parent
        OA oaOld = _ds.getOA_(soid);
        OA oaParent = _ds.getOA_(new SOID(soid.sidx(), oidParent));

        final ResolvedPath pOld = _ds.resolve_(oaOld);
        final ResolvedPath pParent = _ds.resolve_(oaParent);
        checkArgument(!pParent.isUnderOrEqual(pOld), "cannot move object under itself");

        _ds.setOAParentAndName_(oaOld, oaParent, name, t);

        if (updateVersion) {
            _vu.update_(new SOCID(soid, CID.META), t);
        }

        _expulsion.objectMoved_(pOld, soid, op, t);
    }
}
