/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 * discard old FLAG_EXPELLED_INH
 * update value of FLAG_EXPELLED_ORG
 */
public class DPUTUpdateOAFlags implements IDaemonPostUpdateTask
{
    @Inject private IDBCW _dbcw;

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> s.executeUpdate("update " + T_OA
                + " set " + C_OA_FLAGS + "=(" + C_OA_FLAGS + " >> 1) & 1"));
    }
}
