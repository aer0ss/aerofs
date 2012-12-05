/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.update.DPUTUtil.IDatabaseOperation;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.SyncStatusDatabase;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 * This tasks resets persistent sync status data and force re-sending version hash for all admitted
 * objects to make sure sync status (and more importantly aggregate sync status) is brought back to
 * a consistent state whenever a bug that caused inconsistencies is fixed.
 */
public class DPUTResetSyncStatus implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    DPUTResetSyncStatus(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    private void resetBlobColumn(Statement s, String table, String column) throws SQLException
    {
        s.executeUpdate("update " + table + " set " + column + "=NULL");
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, new IDatabaseOperation() {
            @Override
            public void run_(Statement s) throws SQLException
            {
                // no point resetting epochs: pull is reset on signin and push is linked to the
                // auto-increment index of the push queue table...

                // reset status data
                resetBlobColumn(s, T_OA, C_OA_SYNC);
                resetBlobColumn(s, T_OA, C_OA_AG_SYNC);
                resetBlobColumn(s, T_STORE, C_STORE_DIDS);

                // force bootstrap (i.e re-sending vh for all admitted objects)
                SyncStatusDatabase.markAllAdmittedObjectsAsModified(_dbcw.getConnection());
            }
        });
    }
}
