/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;

import java.sql.SQLException;

import static com.aerofs.daemon.lib.db.CoreSchema.C_OA_OOS_CHILDREN;
import static com.aerofs.daemon.lib.db.CoreSchema.C_OA_SYNCED;
import static com.aerofs.daemon.lib.db.CoreSchema.T_OA;

public class DPUTSyncStatusTableAlterations extends PhoenixDPUT
{
    @Inject private IDBCW _dbcw;

    @Override
    public void runPhoenix() throws Exception {
        addSyncStatusColumnsToOA(_dbcw);
    }

    protected static final void addSyncStatusColumnsToOA(IDBCW dbcw) throws SQLException {
        DPUTUtil.runDatabaseOperationAtomically_(dbcw, s -> {
            if (!dbcw.columnExists(T_OA, C_OA_SYNCED)) {
                // add sync column to store table
                s.executeUpdate("alter table " + T_OA + " add column " + C_OA_SYNCED + dbcw.boolType()
                        + "default 1");
                s.executeUpdate("alter table " + T_OA + " add column " + C_OA_OOS_CHILDREN
                        + dbcw.longType() + "default 0");
            }
        });
    }
}
