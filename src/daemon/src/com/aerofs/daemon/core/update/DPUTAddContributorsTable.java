package com.aerofs.daemon.core.update;

import com.aerofs.ids.DID;
import com.aerofs.daemon.lib.db.CoreSchema;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

public class DPUTAddContributorsTable implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTAddContributorsTable(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            CoreSchema.createStoreContributorsTable(s, _dbcw);
            fillContributorsTable(s, _dbcw);

            // now that the contributors table is ready we can cleanup the old
            // index whose only purpose was to speed up getAllVersionDIDs
            deleteVersionIndex(s);
        });
    }

    static void fillContributorsTable(Statement s, IDBCW dbcw) throws SQLException
    {
        Map<Integer, Set<DID>> contrib = Maps.newHashMap();

        fetchContributors(s, contrib, T_VER, C_VER_SIDX, C_VER_DID);
        fetchContributors(s, contrib, T_IV, C_IV_SIDX, C_IV_IMM_DID);

        PreparedStatement ps = dbcw.getConnection().prepareStatement(
                DBUtil.insert(T_SC, C_SC_SIDX, C_SC_DID));

        for (Entry<Integer, Set<DID>> e : contrib.entrySet()) {
            int sidx = e.getKey();
            for (DID did : e.getValue()) {
                ps.setInt(1, sidx);
                ps.setBytes(2, did.getBytes());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void fetchContributors(Statement s, Map<Integer, Set<DID>> contrib,
            String t, String c_sidx, String c_did) throws SQLException
    {
        try (ResultSet rs = s.executeQuery(DBUtil.select(t, "distinct " + c_sidx, c_did))) {
            while (rs.next()) {
                int sidx = rs.getInt(1);
                Set<DID> dids = contrib.get(sidx);
                if (dids == null) {
                    dids = Sets.newHashSet();
                    contrib.put(sidx, dids);
                }
                dids.add(new DID(rs.getBytes(2)));
            }
        }
    }

    private static void deleteVersionIndex(Statement s) throws SQLException
    {
        s.executeUpdate("drop index if exists " + T_VER + "1");
    }
}
