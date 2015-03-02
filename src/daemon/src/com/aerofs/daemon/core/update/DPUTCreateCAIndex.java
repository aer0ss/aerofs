/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreSchema;
import com.aerofs.lib.db.dbcw.IDBCW;

public class DPUTCreateCAIndex implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTCreateCAIndex(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, CoreSchema::createCAIndex);
    }
}
