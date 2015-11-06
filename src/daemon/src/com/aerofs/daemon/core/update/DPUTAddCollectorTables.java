package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.collector.SenderFilterIndex;
import com.aerofs.daemon.lib.db.SyncSchema;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.aerofs.daemon.lib.db.SyncSchema.*;
import static com.google.common.base.Preconditions.checkState;

public class DPUTAddCollectorTables implements IDaemonPostUpdateTask {
    @Inject private IDBCW _dbcw;

    private static final byte[] FULL_BF = new byte[BFOID.HASH.length() / Byte.SIZE];
    static {
        Arrays.fill(FULL_BF, (byte)0xff);
    }

    @Override
    public void run() throws Exception {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            // our internal polaris clients didn't have CF/SF/SD
            if (!_dbcw.tableExists(SyncSchema.T_CF)) {
                SyncSchema.createCollectorTables_(s, _dbcw);

                // to compensate for sender filters not being set, make the first sender filter full
                // the content change epoch used to be the remote change epoch (i.e. largest know
                // but not necessarily applied change on polaris) so it's reasonably safe to assume
                // that the BF won't be discarded too early.
                for (SIndex sidx : stores_(s)) {
                    setSenderFilter_(s, sidx);
                }
            }
        });
    }

    private Iterable<SIndex> stores_(Statement s) throws SQLException {
        List<SIndex> l = new ArrayList<>();
        try (ResultSet rs = s.executeQuery(DBUtil.select(T_STORE, C_STORE_SIDX))) {
            while (rs.next()) {
                l.add(new SIndex(rs.getInt(1)));
            }
        }
        return l;
    }

    private void setSenderFilter_(Statement s, SIndex sidx) throws SQLException {
        try (PreparedStatement ps = s.getConnection().prepareStatement(
                DBUtil.insert(T_SF, C_SF_SIDX, C_SF_SFIDX, C_SF_FILTER))) {
            ps.setInt(1, sidx.getInt());
            ps.setLong(2, SenderFilterIndex.BASE.getLong());
            ps.setBytes(3, FULL_BF);
            checkState(1 == ps.executeUpdate());
        }
    }
}
