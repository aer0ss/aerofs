/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.SyncStatusDatabase;
import com.aerofs.lib.C;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.core.CoreSchema.*;

/**
 * The DB of @aerofs.com users got pretty dirty and possibly inconsistent during the successive
 * testing rounds of sync status (especially during the move to Redis). Worse yet, some significant
 * regression were discovered after the first push of the backend functionnality to a subset of our
 * users.
 *
 * This tasks resets some columns and repopulate the bootstrap table to ensure the DB is back to a
 * clean state re sync status.
 */
public class DPUTResetSyncStatus implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    DPUTResetSyncStatus(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    private void setEpoch(Statement s, String table, String column, long value) throws SQLException
    {
        // epoch table is one row, with one column per epoch
        s.executeUpdate("update " + table + " set " + column + "=" + String.valueOf(value));
    }

    private void resetBlobColumn(Statement s, String table, String column) throws SQLException
    {
        s.executeUpdate("update " + table + " set " + column + "=NULL");
    }

    @Override
    public void run() throws Exception
    {
        Connection c = _dbcw.getConnection();
        assert !c.getAutoCommit();
        Statement s = c.createStatement();
        try {
            // reset pull epoch
            setEpoch(s, T_EPOCH, C_EPOCH_SYNC_PULL, C.INITIAL_SYNC_PULL_EPOCH);
            // set push epoch to end of activity log to avoid duplicate SVH with bootstrap
            long pushEpoch = C.INITIAL_SYNC_PUSH_EPOCH;
            try {
                ResultSet rs = s.executeQuery("select max(" + C_AL_IDX + ") from " + T_AL);
                if (rs.next()) {
                    pushEpoch = rs.getLong(1);
                }
            } catch (SQLException e) {
                // ignore, activity log may be empty
            }
            setEpoch(s, T_EPOCH, C_EPOCH_SYNC_PUSH, pushEpoch);


            // reset status data
            resetBlobColumn(s, T_OA, C_OA_SYNC);
            resetBlobColumn(s, T_OA, C_OA_AG_SYNC);
            resetBlobColumn(s, T_STORE, C_STORE_DIDS);

            // clear bootstrap table to avoid duplicate SVH
            s.executeUpdate("delete from " + T_SSBS);
            // fill bootstrap table again
            SyncStatusDatabase.fillBootstrapTable(c);
        } finally {
            s.close();
        }
        c.commit();
    }
}
