package com.aerofs.daemon.core.update;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static com.aerofs.daemon.lib.db.CoreSchema.*;
import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.google.common.base.Preconditions.checkState;

/**
 * Introduce a new column in the store table to track total space usage by files in the store.
 *
 * Quota enforcement relies on this value and having to scan the whole table on every quota check
 * does not scale when the number of objects grow. Some users with a few hundred thousands files
 * experienced severe sync issues, which prompted this optimization.
 *
 * This DPUT is split in multiple db transactions, to allow incremental progress to be made in case
 * there are multiple large stores.
 */
public class DPUTAddStoreUsageColumn implements IDaemonPostUpdateTask {
    private final static Logger l = LoggerFactory.getLogger(DPUTAddStoreUsageColumn.class);

    @Inject IDBCW _dbcw;

    @Override
    public void run() throws Exception {
        List<Integer> stores = new ArrayList<>();
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            // add usage column if not present
            if (!_dbcw.columnExists(T_STORE, C_STORE_USAGE)) {
                s.executeUpdate("alter table " + T_STORE + " add column "
                        + C_STORE_USAGE + " integer not null default 0");
            }
            // filter out stores whose usage was set in a previous partial run
            try (ResultSet rs = s.executeQuery("select " + C_STORE_SIDX + " from " + T_STORE
                    + " where " + C_STORE_USAGE + "=0")) {
                while (rs.next()) {
                    stores.add(rs.getInt(1));
                }
            }
        });

        for (int sidx : stores) {
            DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
                long total;
                ElapsedTimer t = new ElapsedTimer();
                try (ResultSet rs = s.executeQuery(selectWhere(T_CA, C_CA_SIDX + "=" + sidx,
                        "sum(" + C_CA_LENGTH + ")"))) {
                    checkState(rs.next());
                    total = rs.getLong(1);
                }
                l.info("{} {} bytes {} ms", sidx, total, t.elapsed());
                s.executeUpdate("update " + T_STORE
                        + " set " + C_STORE_USAGE + "=" + total
                        + " where " + C_STORE_SIDX + "=" + sidx);
            });
        }
    }
}
