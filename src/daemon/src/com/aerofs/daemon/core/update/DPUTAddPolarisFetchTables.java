/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.polaris.db.PolarisSchema;
import com.aerofs.daemon.lib.db.SyncSchema;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;

import static com.aerofs.daemon.lib.db.SyncSchema.*;


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

            // add epoch columns to store table
            if (!_dbcw.columnExists(SyncSchema.T_STORE, SyncSchema.C_STORE_LTS_LOCAL)) {
                s.executeUpdate("alter table " + T_STORE
                        + " add column " + C_STORE_LTS_LOCAL + _dbcw.longType());
            }
            if (!_dbcw.columnExists(SyncSchema.T_STORE, SyncSchema.C_STORE_LTS_CONTENT)) {
                s.executeUpdate("alter table " + T_STORE
                        + " add column " + C_STORE_LTS_CONTENT + _dbcw.longType());
            }
            if (!_dbcw.columnExists(SyncSchema.T_STORE, SyncSchema.C_STORE_LTS_HIGHEST)) {
                s.executeUpdate("alter table " + T_STORE
                        + " add column " + C_STORE_LTS_HIGHEST + _dbcw.longType());
            }
        });
    }
}
