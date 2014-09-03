/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.CoreSchema;
import com.aerofs.lib.db.dbcw.IDBCW;

public class DPUTCreateUnlinkedRootTable implements IDaemonPostUpdateTask
{
    private final IDBCW _dcbw;

    public DPUTCreateUnlinkedRootTable(CoreDBCW dbcw)
    {
        _dcbw = dbcw.get();
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dcbw,
                s -> CoreSchema.createUnlinkedRootTable(s, _dcbw));
    }
}
