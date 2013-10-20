package com.aerofs.daemon.core.phy;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.lib.db.trans.Trans;

import java.io.IOException;
import java.sql.SQLException;

public interface IPhysicalFolder extends IPhysicalObject
{
    /**
     * Perform all steps to *physically* promote a regular folder to an anchor
     *
     * For instance, LinkedStorage will use that to set a special icon for the folder on platform
     * that support it.
     */
    void promoteToAnchor_(SID sid, PhysicalOp op, Trans t)
            throws IOException, SQLException;

    /**
     * Perform all steps to *physically* demote an anchor to a regular folder
     */
    void demoteToRegularFolder_(SID sid, PhysicalOp op, Trans t)
            throws IOException, SQLException;
}
