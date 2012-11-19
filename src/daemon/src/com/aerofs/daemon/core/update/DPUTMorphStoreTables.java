/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.update.DPUTUtil.IDatabaseOperation;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.CoreSchema;
import static com.aerofs.lib.db.CoreSchema.*;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

/**
 * Update store table schemas to support multi-user systems
 */
public class DPUTMorphStoreTables implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    private static final String C_STORE_PARENT = "s_p";

    DPUTMorphStoreTables(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, new IDatabaseOperation()
        {
            @Override
            public void run_(Statement s) throws SQLException
            {
                morph_(s);
            }
        });
    }

    private static class StoreRow {
        SIndex _sidx;
        SIndex _sidxParent;
        @Nullable byte[] _dids;
    }

    private void morph_(Statement s)
            throws SQLException
    {
        List<StoreRow> srs = getStoreRowsFromOldTable_(s);

        s.executeUpdate("drop table " + T_STORE);

        CoreSchema.createStoreTables(s, _dbcw);

        populateStoreTable_(srs);
        populateStoreParentTable_(srs);
    }

    private List<StoreRow> getStoreRowsFromOldTable_(Statement s)
            throws SQLException
    {
        List<StoreRow> srs = Lists.newArrayList();

        ResultSet rs = s.executeQuery(DBUtil.selectFrom(T_STORE, C_STORE_SIDX,
                C_STORE_PARENT, C_STORE_DIDS));
        try {
            while (rs.next()) {
                StoreRow sr = new StoreRow();
                sr._sidx = new SIndex(rs.getInt(1));
                assert !rs.wasNull();
                sr._sidxParent = new SIndex(rs.getInt(2));
                assert !rs.wasNull();
                sr._dids = rs.getBytes(3);
                srs.add(sr);
            }
        } finally {
            rs.close();
        }

        return srs;
    }

    private void populateStoreTable_(List<StoreRow> srs)
            throws SQLException
    {
        PreparedStatement ps = _dbcw.getConnection().prepareStatement(
                DBUtil.insert(T_STORE, C_STORE_SIDX, C_STORE_DIDS));
        for (StoreRow sr : srs) {
            ps.setInt(1, sr._sidx.getInt());
            if (sr._dids == null) ps.setNull(2, Types.BLOB);
            else ps.setBytes(2, sr._dids);
            ps.addBatch();
        }
        ps.executeBatch();
    }

    private void populateStoreParentTable_(List<StoreRow> srs)
            throws SQLException
    {
        PreparedStatement ps = _dbcw.getConnection().prepareStatement(
                DBUtil.insert(T_SH, C_SH_SIDX, C_SH_PARENT_SIDX));
        for (StoreRow sr : srs) {
            // Do not add the parent for root stores. In the old store table, the root store's
            // parent is the store itself. In the new table, root stores don't have parents.
            if (sr._sidx.equals(sr._sidxParent)) continue;
            ps.setInt(1, sr._sidx.getInt());
            ps.setInt(2, sr._sidxParent.getInt());
            ps.addBatch();
        }
        ps.executeBatch();
    }
}
