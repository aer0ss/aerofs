/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.db.dbcw.IDBCW;

import static com.aerofs.daemon.lib.db.CoreSchema.C_EPOCH_AUDIT_PUSH;
import static com.aerofs.daemon.lib.db.CoreSchema.T_EPOCH;

/**
 * Add the column required to track the last-reported activity log row to the epoch table.
 * <p/>
 * <strong>IMPORTANT:</strong> Although this column is not used
 * in hybrid installations, there is absolutely no problem with
 * adding it there. This allows us to keep the database schema
 * identical regardless of installation type.
 */
public class DPUTUpdateEpochTableAddAuditColumn implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    DPUTUpdateEpochTableAddAuditColumn(CoreDBCW dbcw) {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws Exception {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            if (!_dbcw.columnExists(T_EPOCH, C_EPOCH_AUDIT_PUSH)) {
                s.executeUpdate("alter table " + T_EPOCH +
                        " add column " + C_EPOCH_AUDIT_PUSH + _dbcw.longType() + "not null default " + LibParam.INITIAL_AUDIT_PUSH_EPOCH);
            }
        });
    }
}
