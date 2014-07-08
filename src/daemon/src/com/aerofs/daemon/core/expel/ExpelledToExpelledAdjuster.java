package com.aerofs.daemon.core.expel;

import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import static com.google.common.base.Preconditions.checkArgument;

class ExpelledToExpelledAdjuster implements IExpulsionAdjuster
{
    private final LogicalStagingArea _sa;

    @Inject
    public ExpelledToExpelledAdjuster(LogicalStagingArea sa)
    {
        _sa = sa;
    }

    @Override
    public void adjust_(ResolvedPath pathOld, SOID soid, PhysicalOp op, Trans t) throws Exception
    {
        checkArgument(pathOld.soid().equals(soid));
        _sa.preserveStaging_(pathOld, t);
    }
}
