/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.launch_tasks;

import com.aerofs.base.BaseParam.Audit;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.update.DPUTUtil;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.IAuditDatabase;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.lib.db.CoreSchema.*;
import static com.google.common.base.Preconditions.checkState;

/**
 * Activity log should not grow unbounded because that's just bad for business
 *
 * Case in point, one BB user ended up with a 3.2G db file, of which 99.3% was taken by the
 * activity log table (just short of 24M entries for a mere 10k files).
 *
 * To keep the activity log within reasonable bounds a cleanup task is started on every launch.
 * If the activity log contains more than 10k entries it is cleaned up incrementally: every 10s
 * the first 1k entries are removed until the size falls back under the high water mark.
 *
 * NB: activity log is currently used for audit trails. This will change with the introduction
 * of the centralized metadata server but for now that means care must be taken to keep the
 * cleaner *behind* the audit event reporter.
 */
public class DLTCleanActivityLog extends DaemonLaunchTask
{
    private final static Logger l = Loggers.getLogger(DLTCleanActivityLog.class);

    private final static long CLEANUP_THRESHOLD = 10000;
    private final static long CLEANUP_CHUNK_SIZE = 1000;
    private final static long DELAY_BETWEEN_CHUNKS = 20 * C.SEC;

    private final IDBCW _dbcw;
    private final IAuditDatabase _auditdb;

    @Inject
    public DLTCleanActivityLog(CoreScheduler sched, CoreDBCW dbcw, IAuditDatabase auditdb)
    {
        super(sched);
        _dbcw = dbcw.get();
        _auditdb = auditdb;
    }

    @Override
    protected void run_()
    {
        try {
            final long lastReported = _auditdb.getLastReportedActivityRow_();

            DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
                final long min = selectIdx_(s, "min(" + C_AL_IDX + ")");
                final long max = selectIdx_(s, "max(" + C_AL_IDX + ")");

                // derive highest activity index that may be cleaned given that:
                //    * we always preserve the last few entries
                //    * we should not clean unreported activities if audit is enabled
                final long maxCleanable = bound(max - CLEANUP_THRESHOLD, lastReported);

                final long numCleanable = maxCleanable - min;

                if (maxCleanable > 0) {
                    long chunkSize = Math.min(numCleanable, CLEANUP_CHUNK_SIZE);
                    delete_(s, min + chunkSize, max);
                }
            });
        } catch (SQLException e) {
            l.warn("failed to cleanup activity log", e);
        }

        _sched.schedule(this, DELAY_BETWEEN_CHUNKS);
    }

    private static long bound(long val, long max)
    {
        return Audit.AUDIT_ENABLED ? Math.min(val, max) : val;
    }

    private static long selectIdx_(Statement s, String expr) throws SQLException
    {
        try (ResultSet rs = s.executeQuery(DBUtil.select(T_AL, expr))) {
            checkState(rs.next());
            long r = rs.getLong(1);
            checkState(!rs.next());
            return r;
        }
    }

    private static void delete_(Statement s, long lowerBound, long max) throws SQLException
    {
        int rows = s.executeUpdate(DBUtil.deleteWhere(T_AL, C_AL_IDX + " < " + lowerBound));
        l.info("cleaned {} activity entries, up to {}/{}", rows, lowerBound, max);
    }
}
