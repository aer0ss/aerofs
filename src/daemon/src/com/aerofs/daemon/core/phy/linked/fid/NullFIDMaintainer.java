package com.aerofs.daemon.core.phy.linked.fid;

import com.aerofs.daemon.lib.db.trans.Trans;

/**
 * This implementation is used when no FID maintenance work is required for physical objects that
 * own the maintainer.
 */
public class NullFIDMaintainer implements IFIDMaintainer
{
    @Override
    public void physicalObjectCreated_(Trans t)
    {
    }

    @Override
    public void physicalObjectMoved_(IFIDMaintainer fidm, Trans t)
    {
    }

    @Override
    public void physicalObjectDeleted_(Trans t)
    {
    }
}
