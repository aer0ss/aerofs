package com.aerofs.daemon.core.expel;

import java.sql.SQLException;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

class ExpelledToExpelledAdjuster implements IExpulsionAdjuster
{
    private final DirectoryService _ds;

    @Inject
    ExpelledToExpelledAdjuster(DirectoryService ds)
    {
        _ds = ds;
    }

    @Override
    public void adjust_(boolean emigrate, PhysicalOp op, SOID soid, ResolvedPath pOld, int flags, Trans t)
            throws SQLException
    {
        _ds.setOAFlags_(soid, flags, t);
    }
}
