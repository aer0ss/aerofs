/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser.migration;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.migration.ImmigrantDetector;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.base.ex.ExNotFound;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.SQLException;

public class NullImmigrantDetector extends ImmigrantDetector
{
    @Inject
    public void inject_(DirectoryService ds, NativeVersionControl nvc,
            ImmigrantVersionControl ivc, IPhysicalStorage ps)
    {
        baseInject_(ds, nvc, ivc, ps);
    }

    @Override
    public boolean detectAndPerformImmigration_(@Nonnull OA oaTo, PhysicalOp op, Trans t)
            throws SQLException, IOException, ExNotFound, ExAlreadyExist, ExNotDir
    {
        return false;
    }
}
