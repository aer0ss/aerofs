package com.aerofs.daemon.core.expel;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map.Entry;

import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.Path;
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
    public void adjust_(boolean emigrate, PhysicalOp op, SOID soid, Path pOld, int flags, Trans t)
            throws IOException, SQLException
    {
        assert !emigrate;

        _ds.setOAFlags_(soid, flags, t);

        OA oa = _ds.getOA_(soid);
        if (oa.isFile()) {
            for (Entry<KIndex, CA> en : oa.cas().entrySet()) {
                SOKID sokid = new SOKID(soid, en.getKey());
                IPhysicalFile pfOld = _ps.newFile_(sokid, pOld);
                IPhysicalFile pfNew = en.getValue().physicalFile();
                pfOld.move_(pfNew, op, t);
            }
        } else {
            assert oa.isDirOrAnchor();
            IPhysicalFolder pfOld = _ps.newFolder_(soid, pOld);
            IPhysicalFolder pfNew = oa.physicalFolder();
            pfOld.move_(pfNew, op, t);
        }
    }
}
