/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.CoreSchema;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class DPUTCreateActivityLogTables implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    DPUTCreateActivityLogTables(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws SQLException
    {
        Connection c = _dbcw.getConnection();
        assert !c.getAutoCommit();
        Statement s = c.createStatement();
        try {
            CoreSchema.createActivityLogTables(s, _dbcw);
        } finally {
            s.close();
        }
        c.commit();
    }
}
