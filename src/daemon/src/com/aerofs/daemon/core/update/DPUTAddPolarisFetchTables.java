/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.polaris.db.PolarisSchema;
import com.aerofs.lib.db.dbcw.IDBCW;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

public class DPUTAddPolarisFetchTables implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTAddPolarisFetchTables(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            PolarisSchema.createPolarisFetchTables(s, _dbcw);
        });
    }
}
