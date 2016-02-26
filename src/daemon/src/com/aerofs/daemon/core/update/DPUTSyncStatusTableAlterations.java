/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.lib.db.CoreSchema.C_OA_OOS_CHILDREN;
import static com.aerofs.daemon.lib.db.CoreSchema.C_OA_SYNCED;
import static com.aerofs.daemon.lib.db.CoreSchema.T_OA;

public class DPUTSyncStatusTableAlterations implements IDaemonPostUpdateTask
{
    @Inject private IDBCW _dbcw;

    @Override
    public void run() throws Exception {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            if (!_dbcw.columnExists(T_OA, C_OA_SYNCED)) {
                addSyncStatusColumnsToOA(s);
            }
        });
    }

    private final void addSyncStatusColumnsToOA(Statement s) throws SQLException {
        // add sync column to store table
        s.executeUpdate(
                "alter table " + T_OA + " add column " + C_OA_SYNCED + _dbcw.boolType() + "default 1");
        s.executeUpdate("alter table " + T_OA + " add column " + C_OA_OOS_CHILDREN + _dbcw.longType()
                + "default 0");
    }
}
