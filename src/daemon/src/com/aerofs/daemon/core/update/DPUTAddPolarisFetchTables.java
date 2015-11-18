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
            // we internally started using polaris already...
            // also, due to staggered rollout this must be idempotent
            if (!_dbcw.tableExists(PolarisSchema.T_VERSION)) {
                PolarisSchema.createPolarisFetchTables(s, _dbcw);
            }
        });
    }
}
