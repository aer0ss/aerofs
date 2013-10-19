package com.aerofs.daemon.core.object;

import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.multiplicity.singleuser.migration.ImmigrantCreator;
import com.aerofs.daemon.core.phy.PhysicalOp;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.CID;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;

import javax.inject.Inject;
import java.io.IOException;
import java.sql.SQLException;

public class ObjectMover
{
    private DirectoryService _ds;
    private VersionUpdater _vu;
    private Expulsion _expulsion;
    private ImmigrantCreator _imc;

    @Inject
    public void inject_(VersionUpdater vu, DirectoryService ds, Expulsion expulsion,
            ImmigrantCreator imc)
    {
        _vu = vu;
        _ds = ds;
        _imc = imc;
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

        final ResolvedPath pOld = _ds.resolve_(oaOld);

        _ds.setOAParentAndName_(oaOld, oaParent, name, t);

        if (updateVersion) {
            SOCKID k = new SOCKID(soid, CID.META);
            _vu.update_(k, t);
        }

        _expulsion.objectMoved_(emigrate, op, soid, pOld, t);
    }

    /**
     * This method either moves objects within the same store, or across stores via migration,
     * depending on whether the old sidx is the same as the new one.
     *
     * @return the SOID of the object after the move. This new SOID may be different from
     * the parameter {@code soid} if migration occurs.
     *
     * Note: This is a method operate at the top most level, while ObjectMover and ImmigrantCreator
     * operate at lower levels. That's why we didn't put the method to ObjectMover. Also because
     * ObjectMover operates at a level even lower than ImmigrantCreator, having the method in
     * ObjectMover would require this class to refer to ImmigrantCreator, which is inappropriate.
     */
    public SOID move_(SOID soid, SOID soidToParent, String toName, PhysicalOp op, Trans t)
            throws Exception
    {
        if (soidToParent.sidx().equals(soid.sidx())) {
            moveInSameStore_(soid, soidToParent.oid(), toName, op, false, true, t);
            return soid;
        } else {
            return _imc.createImmigrantRecursively_(
                    _ds.resolve_(soid).parent(), soid, soidToParent, toName, op, t);
        }
    }
}
