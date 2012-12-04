package com.aerofs.daemon.core.object;

import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.phy.PhysicalOp;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;

import javax.inject.Inject;
import java.io.IOException;
import java.sql.SQLException;

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
      * TODO avoid moving a directory to a subdir of itself, or moving root dir
      * @param emigrate whether the move results from emigration
      */
    public void moveInSameStore_(final SOID soid, OID oidParent, String name, PhysicalOp op,
            boolean emigrate, boolean updateVersion, Trans t)
            throws SQLException, ExAlreadyExist, ExNotDir, IOException, ExNotFound, ExStreamInvalid
    {
        // The caller must guarantee the local existence of the object and its new parent
        OA oaOld = _ds.getOA_(soid);
        OA oaParent = _ds.getOA_(new SOID(soid.sidx(), oidParent));

        final Path pOld = _ds.resolve_(oaOld);

        _ds.setOAParentAndName_(oaOld, oaParent, name, t);

        if (updateVersion) {
            SOCKID k = new SOCKID(soid, CID.META);
            _vu.update_(k, t);
        }

        _expulsion.objectMoved_(emigrate, op, soid, pOld, t);
    }
}
