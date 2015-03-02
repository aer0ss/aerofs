/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;

public class DPUTMakeMTimesNaturalNumbersOnly implements IDaemonPostUpdateTask
{

    private final IDBCW _dbcw;

    public DPUTMakeMTimesNaturalNumbersOnly(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> s.executeUpdate(
                DBUtil.updateWhere(T_CA, C_CA_MTIME + "<0", C_CA_MTIME)));
    }
}
