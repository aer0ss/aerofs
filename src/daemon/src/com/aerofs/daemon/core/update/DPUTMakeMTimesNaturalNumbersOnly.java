/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import static com.aerofs.daemon.core.CoreSchema.*;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.Connection;
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
        Connection c = _dbcw.getConnection();
        assert !c.getAutoCommit();
        Statement s = c.createStatement();
        try {
            s.executeUpdate("update " + T_CA + " set " + C_CA_MTIME + "=0 WHERE " +
                C_CA_MTIME + "<0");
        } finally {
            s.close();
        }
        c.commit();
    }
}
