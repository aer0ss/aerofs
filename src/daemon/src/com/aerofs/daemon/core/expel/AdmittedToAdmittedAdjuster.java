package com.aerofs.daemon.core.expel;

import java.io.IOException;
import java.sql.SQLException;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import static com.google.common.base.Preconditions.checkState;

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
    public void adjust_(ResolvedPath pathOld, SOID soid, PhysicalOp op, Trans t)
            throws IOException, SQLException
    {
        checkState(!pathOld.isEmpty());
        checkState(soid.equals(pathOld.soid()));
        OA oa = _ds.getOA_(soid);
        ResolvedPath pNew = _ds.resolve_(oa);
        if (oa.isFile()) {
            for (KIndex kidx : oa.cas().keySet()) {
                _ps.newFile_(pathOld, kidx).move_(pNew, kidx, op, t);
            }
        } else {
            checkState(oa.isDirOrAnchor());
            _ps.newFolder_(pathOld).move_(pNew, op, t);
        }
    }
}
