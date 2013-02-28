/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

import com.aerofs.daemon.core.update.DPUTUtil.IDatabaseOperation;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.SQLException;
import java.sql.Statement;

public class DPUTMakeMTimesNaturalNumbersOnly implements IDaemonPostUpdateTask
{

    private final IDBCW _dbcw;

    public DPUTMakeMTimesNaturalNumbersOnly(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, new IDatabaseOperation() {
            @Override
            public void run_(Statement s) throws SQLException
            {
                s.executeUpdate("update " + T_CA + " set " + C_CA_MTIME + "=0 WHERE " +
                        C_CA_MTIME + "<0");
            }
        });
    }
}
