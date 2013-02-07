package com.aerofs.daemon.core.phy;

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
     *
     * @pre the SOID passed to the constructor must be the root dir of a store
     */
    void promoteToAnchor_(Trans t) throws IOException, SQLException;

    /**
     * Perform all steps to *physically* demote an anchor to a regular folder
     *
     * @pre the SOID passed to the constructor must be the root dir of a store
     */
    void demoteToRegularFolder_(Trans t) throws IOException, SQLException;
}
