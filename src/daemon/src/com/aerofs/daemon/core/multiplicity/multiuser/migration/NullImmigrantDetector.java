/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser.migration;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.migration.IImmigrantDetector;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.SQLException;

public class NullImmigrantDetector implements IImmigrantDetector
{
    @Override
    public boolean detectAndPerformImmigration_(@Nonnull OA oaTo, PhysicalOp op, Trans t)
            throws SQLException, IOException, ExNotFound, ExAlreadyExist, ExNotDir, ExStreamInvalid
    {
        return false;
    }
}
