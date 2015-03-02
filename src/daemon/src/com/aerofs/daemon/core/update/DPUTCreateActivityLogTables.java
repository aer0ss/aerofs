/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreSchema;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.SQLException;

final class DPUTCreateActivityLogTables implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    DPUTCreateActivityLogTables(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    @Override
    public void run() throws SQLException
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw,
                s -> CoreSchema.createActivityLogTables(s, _dbcw));
    }
}
