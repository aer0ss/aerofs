package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.dbcw.IDBCW;

import static com.aerofs.daemon.lib.db.CoreSchema.T_PD;

/**
 * Early versions of {@link DPUTCleanupGhostKML} did not clear the table of pulled devices
 * which could cause bloom filters to be lost leading to nosync.
 *
 * NOTE: this forced refresh of bloom filters is slightly aggressive (i.e. it may cause a
 * significant number of false positives in the next collector loop, thereby wasting cpu
 * cycles and network bandwidth) however the system should settle back to a steady state
 * fairly rapidly so it was not deemed worth investing more time in a more granular refresh.
 */
public class DPUTRefreshBloomFilters implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTRefreshBloomFilters(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> s.executeUpdate("delete from " + T_PD));
    }
}
