/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import static com.aerofs.daemon.lib.db.CoreSchema.C_EPOCH_SYNC_PULL;
import static com.aerofs.daemon.lib.db.CoreSchema.T_EPOCH;

import java.sql.SQLException;
import java.sql.Statement;

import com.aerofs.daemon.core.update.DPUTUtil.IDatabaseOperation;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.db.dbcw.IDBCW;

public class DPUTUpdateEpochTable implements IDaemonPostUpdateTask {
    private final IDBCW _dbcw;

    DPUTUpdateEpochTable(CoreDBCW dbcw) {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws Exception {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, new IDatabaseOperation() {
            @Override
            public void run_(Statement s) throws SQLException
            {
                if (!_dbcw.columnExists(T_EPOCH, C_EPOCH_SYNC_PULL)) {
                    s.executeUpdate("alter table " + T_EPOCH +
                            " add column " + C_EPOCH_SYNC_PULL + _dbcw.longType());
                    s.executeUpdate("update " + T_EPOCH +
                            " set " + C_EPOCH_SYNC_PULL + "=" + LibParam.INITIAL_SYNC_PULL_EPOCH);
                }
            }
        });
    }
}
