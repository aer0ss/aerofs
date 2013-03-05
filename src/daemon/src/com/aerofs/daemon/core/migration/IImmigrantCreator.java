/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.migration;

import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.SOID;

import java.io.IOException;
import java.sql.SQLException;

public interface IImmigrantCreator
{
    /**
     * Recursively migrate the object corresponding to {@code soidFromRoot} to
     * under {@code soidToRootParent}.
     *
     * This method assumes that permissions have been checked.
     *
     * @param soidFromRoot the SOID of the root object to be migrated
     * @param soidToRootParent the SOID of the parent to which the root object will be migrated
     * @return the new SOID of the root object
     */
    public SOID createImmigrantRecursively_(final SOID soidFromRoot, SOID soidToRootParent,
            final String toRootName, final PhysicalOp op, final Trans t)
            throws ExStreamInvalid, IOException, ExNotFound, ExAlreadyExist, SQLException, ExNotDir;
}
