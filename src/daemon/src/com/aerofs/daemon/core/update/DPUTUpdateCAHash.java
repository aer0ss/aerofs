/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.C;
import com.aerofs.lib.db.dbcw.IDBCW;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 * A change in the content hashing model makes old hashes for files larger than 4Mb invalid.
 *
 * Instead of going through the painful process of re-hashing all these large files, we simply
 * lossen assumptions on the existence of content hashes for conflict branches, which allows us
 * to discard old invalid hashes.
 */
public class DPUTUpdateCAHash implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTUpdateCAHash(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> s.executeUpdate("update " + T_CA
                + " set " + C_CA_HASH + "=null"
                + " where " + C_CA_LENGTH + ">" + (4 * C.MB)));
    }
}
