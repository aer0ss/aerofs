/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.CoreSchema;
import com.aerofs.lib.db.dbcw.IDBCW;

public class DPUTAddLogicalStagingArea implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTAddLogicalStagingArea(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw,
                s -> CoreSchema.createLogicalStagingArea(s, _dbcw));
    }
}
