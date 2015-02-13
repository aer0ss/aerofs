package com.aerofs.daemon.core.phy;

import com.aerofs.ids.SID;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.lib.db.trans.Trans;

import java.io.IOException;
import java.sql.SQLException;

public interface IPhysicalFolder extends IPhysicalObject
{
    /**
     * @throws IOException if the object doesn't exist, or the target object already
     * exists, or other I/O error occurs
     *
     * N.B. must roll back the operation if the transaction is aborted
     */
    void move_(ResolvedPath to, PhysicalOp op, Trans t)
            throws IOException, SQLException;

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
