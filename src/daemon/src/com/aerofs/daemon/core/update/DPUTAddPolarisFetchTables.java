/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.polaris.db.PolarisSchema;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;


public class DPUTAddPolarisFetchTables implements IDaemonPostUpdateTask
{
    @Inject private IDBCW _dbcw;

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            PolarisSchema.createPolarisFetchTables(s, _dbcw);
        });
    }
}
