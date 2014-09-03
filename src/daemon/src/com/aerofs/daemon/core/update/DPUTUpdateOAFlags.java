/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.dbcw.IDBCW;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 * discard old FLAG_EXPELLED_INH
 * update value of FLAG_EXPELLED_ORG
 */
public class DPUTUpdateOAFlags implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTUpdateOAFlags(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> s.executeUpdate("update " + T_OA
                + " set " + C_OA_FLAGS + "=(" + C_OA_FLAGS + " >> 1) & 1"));
    }
}
