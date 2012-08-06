/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.CoreSchema;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.injectable.InjectableDriver;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class DPUTCreateActivityLogTables implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;
    private final InjectableDriver _dr;

    DPUTCreateActivityLogTables(CoreDBCW dbcw, InjectableDriver dr)
    {
        _dbcw = dbcw.get();
        _dr = dr;
    }

    @Override
    public void run() throws SQLException
    {
        Connection c = _dbcw.getConnection();
        assert !c.getAutoCommit();
        Statement s = c.createStatement();
        try {
            CoreSchema cs = new CoreSchema(_dbcw, _dr);
            cs.createActivityLogTables(s);
        } finally {
            s.close();
        }
        c.commit();
    }
}
