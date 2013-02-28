package com.aerofs.daemon.core.update;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

import java.sql.SQLException;
import java.sql.Statement;

import com.aerofs.daemon.core.update.DPUTUtil.IDatabaseOperation;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.Param;
import com.aerofs.lib.db.dbcw.IDBCW;

public class DPUTUpdateSchemaForSyncStatus implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTUpdateSchemaForSyncStatus(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    private void addBlobColumnIfNotExists(Statement s, String table, String columnName)
        throws SQLException
    {
        if (!_dbcw.columnExists(table, columnName)) {
            s.executeUpdate("alter table " + table + " add column " + columnName + " blob");
        }
    }

    /**
     * For clients installed after activity log is enabled, sync status need to be bootstrapped
     * by sending to the server version hashes for all non-expelled objects
     */
    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, new IDatabaseOperation() {
            @Override
            public void run_(Statement s) throws SQLException
            {
                // Add missing columns
                addBlobColumnIfNotExists(s, T_STORE, C_STORE_DIDS);
                addBlobColumnIfNotExists(s, T_OA, C_OA_SYNC);
                if (!_dbcw.columnExists(T_EPOCH, C_EPOCH_SYNC_PUSH)) {
                    s.executeUpdate("alter table " + T_EPOCH +
                            " add column " + C_EPOCH_SYNC_PUSH + _dbcw.longType());
                    s.executeUpdate("update " + T_EPOCH +
                            " set " + C_EPOCH_SYNC_PUSH + "=" + Param.INITIAL_SYNC_PUSH_EPOCH);
                }
            }
        });
    }
}
