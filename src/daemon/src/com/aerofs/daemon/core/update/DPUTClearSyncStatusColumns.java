/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.update.DPUTUtil.IDatabaseOperation;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.lib.db.CoreSchema.T_OA;
import static com.aerofs.daemon.lib.db.CoreSchema.T_STORE;

/**
 * Wipe the data in the columns specific to Sync Status to save some space. Since deleting columns
 * in SQLite is non-trivial, we only remove their data.
 */
public class DPUTClearSyncStatusColumns implements IDaemonPostUpdateTask
{
    private final static String
            C_STORE_DIDS  = "s_d",
            C_OA_SYNC     = "o_st",
            C_OA_AG_SYNC  = "o_as";

    private final IDBCW _dbcw;

    public DPUTClearSyncStatusColumns(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, new IDatabaseOperation() {
            @Override
            public void run_(Statement s) throws SQLException
            {
                clearColumn(s, T_STORE, C_STORE_DIDS);
                clearColumn(s, T_OA, C_OA_SYNC);
                clearColumn(s, T_OA, C_OA_AG_SYNC);
            }
        });
    }

    private void clearColumn(Statement s, String table, String column)
            throws SQLException
    {
        if (_dbcw.columnExists(table, column)) {
            s.executeUpdate("update " + table + " set " + column + "=null");
        }
    }
}
