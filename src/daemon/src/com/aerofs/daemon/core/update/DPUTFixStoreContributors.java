/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.lib.db.dbcw.IDBCW;

import static com.aerofs.daemon.lib.db.CoreSchema.T_SC;

/**
 * The was a bug in the initialization of the contributors table in the original implementation of
 * {@link DPUTAddContributorsTable}, which could result in no-sync under certain circumstances.
 */
public class DPUTFixStoreContributors implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTFixStoreContributors(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            // clean table to avoid constraint violation when inserting
            s.executeUpdate("delete from " + T_SC);
            DPUTAddContributorsTable.fillContributorsTable(s, _dbcw);
        });
    }
}
