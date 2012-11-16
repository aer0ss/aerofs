/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.migration;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.SQLException;

public interface IImmigrantDetector
{
    /**
     * this method assumes that:
     *
     * 1) the destination is admitted
     * 2) permissions have been checked
     * 3) the destination metadata has been created
     * 4) no content exists in the destination object if it is a file
     * 5) no child store exists in the destination object if it is an anchor
     *
     * @param oaTo the OA of the destination object
     * @return true if immigration has been performed
     */
    boolean detectAndPerformImmigration_(@Nonnull OA oaTo, PhysicalOp op, Trans t)
            throws SQLException, IOException, ExNotFound, ExAlreadyExist, ExNotDir, ExStreamInvalid;
}
