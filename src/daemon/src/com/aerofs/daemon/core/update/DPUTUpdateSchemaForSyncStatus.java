package com.aerofs.daemon.core.update;

import static com.aerofs.lib.db.CoreSchema.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.SyncStatusDatabase;
import com.aerofs.lib.C;
import com.aerofs.lib.db.CoreSchema;
import com.aerofs.lib.db.dbcw.IDBCW;

public class DPUTUpdateSchemaForSyncStatus implements IDaemonPostUpdateTask {
    private final IDBCW _dbcw;

    public DPUTUpdateSchemaForSyncStatus(CoreDBCW dbcw) {
        _dbcw = dbcw.get();
    }

    private void addBlobColumnIfNotExists(Statement s, String table, String columnName)
        throws SQLException {
        if (!_dbcw.columnExists(table, columnName))
            s.executeUpdate("alter table " + table + " add column " + columnName + " blob");
    }

    /**
     * For clients installed after activity log is enabled, sync status need to be bootstrapped
     * by sending to the server version hashes for all non-expelled objects
     */
    @Override
    public void run() throws Exception {
        Connection c = _dbcw.getConnection();
        assert !c.getAutoCommit();
        Statement s = c.createStatement();
        try {
            // Add missing columns
            addBlobColumnIfNotExists(s, T_STORE, C_STORE_DIDS);
            addBlobColumnIfNotExists(s, T_OA, C_OA_SYNC);
            if (!_dbcw.columnExists(T_EPOCH, C_EPOCH_SYNC_PUSH)) {
                s.executeUpdate("alter table " + T_EPOCH +
                                " add column " + C_EPOCH_SYNC_PUSH + _dbcw.longType());
                s.executeUpdate("update " + T_EPOCH +
                                " set " + C_EPOCH_SYNC_PUSH + "=" + C.INITIAL_SYNC_PUSH_EPOCH);
            }

            // create and fill bootstrap table
            if (!_dbcw.tableExists(T_SSBS)) {
                CoreSchema.createSyncStatusBootstrapTable(s, _dbcw);
                SyncStatusDatabase.fillBootstrapTable(c);
            }
        } finally {
            s.close();
        }
        c.commit();
    }
}
