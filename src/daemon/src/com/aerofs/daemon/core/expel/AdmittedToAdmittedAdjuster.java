package com.aerofs.daemon.core.expel;

import java.io.IOException;
import java.sql.SQLException;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

class AdmittedToAdmittedAdjuster implements IExpulsionAdjuster
{
    private final IPhysicalStorage _ps;
    private final DirectoryService _ds;

    @Inject
    public AdmittedToAdmittedAdjuster(IPhysicalStorage ps, DirectoryService ds)
    {
        _ps = ps;
        _ds = ds;
    }

    @Override
    public void adjust_(boolean emigrate, PhysicalOp op, SOID soid, ResolvedPath pOld, int flags, Trans t)
            throws IOException, SQLException
    {
        assert !emigrate;

        _ds.setOAFlags_(soid, flags, t);

        OA oa = _ds.getOA_(soid);
        ResolvedPath pNew = _ds.resolve_(oa);
        if (oa.isFile()) {
            for (KIndex kidx : oa.cas().keySet()) {
                IPhysicalFile pfOld = _ps.newFile_(pOld, kidx);
                IPhysicalFile pfNew = _ps.newFile_(pNew, kidx);
                pfOld.move_(pfNew, op, t);
            }
        } else {
            assert oa.isDirOrAnchor();
            IPhysicalFolder pfOld = _ps.newFolder_(pOld);
            IPhysicalFolder pfNew = _ps.newFolder_(pNew);
            pfOld.move_(pfNew, op, t);
        }
    }
}
