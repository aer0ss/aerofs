package com.aerofs.daemon.core.update;

import static com.aerofs.lib.db.CoreSchema.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.C;
import com.aerofs.lib.db.CoreSchema;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;

public class DPUTUpdateSchemaForSyncStatus implements IDaemonPostUpdateTask {
    private final IDBCW _dbcw;

    public DPUTUpdateSchemaForSyncStatus(CoreDBCW dbcw) {
        _dbcw = dbcw.get();
    }

    /**
     * SQLite only : check whether a table exists
     * @throws SQLException
     */
    private boolean tableExists(Statement s, String tableName) throws SQLException {
        boolean ok = false;
        ResultSet rs = s.executeQuery("select name from sqlite_master" +
                                      " where type='table' and name='" + tableName + "'");
        try {
            ok = rs.next();
        } finally {
            rs.close();
        }
        return ok;
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
            if (!tableExists(s, T_SSBS)) {
                List<SOID> soids = new ArrayList<SOID>();
                // Only expelled objects and the root anchor have NULL FID
                ResultSet rs = s.executeQuery("select " + C_OA_SIDX + "," + C_OA_OID + " from " + T_OA +
                                              " where " + C_OA_FID + " is not null");
                try {
                    while (rs.next()) {
                        soids.add(new SOID(new SIndex(rs.getInt(1)), new OID(rs.getBytes(2))));
                    }
                } finally {
                    rs.close();
                }
                CoreSchema.createSyncStatusBootstrapTable(s, _dbcw);
                addBootstrapSOIDs(c, soids);
            }
        } finally {
            s.close();
        }
        c.commit();
    }

    /**
     * Add a list of SOIDs to the bootstrap table
     *
     * NOTE: this is only public to avoid exposing the DB schema in unit tests
     */
    public static void addBootstrapSOIDs(Connection c, Iterable<SOID> soids) throws SQLException
    {
        PreparedStatement ps = c.prepareStatement("insert into " + T_SSBS +
                " (" + C_SSBS_SIDX + "," + C_SSBS_OID + ")" +
                " values(?,?)");
        for (SOID soid : soids) {
            ps.setInt(1, soid.sidx().getInt());
            ps.setBytes(2, soid.oid().getBytes());
            ps.addBatch();
        }
        ps.executeBatch();
    }
}
