package com.aerofs.daemon.core.expel;

import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;

class ExpelledToExpelledAdjuster implements IExpulsionAdjuster
{
    @Override
    public void adjust_(ResolvedPath pathOld, SOID soid, boolean emigrate, PhysicalOp op, Trans t)
    { }
}
