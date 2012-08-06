/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.CoreSchema;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.Connection;
import java.sql.Statement;

import static com.aerofs.lib.db.CoreSchema.*;

final class DPUTOptimizeCSTableIndex implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    DPUTOptimizeCSTableIndex(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    /**
     * The index cs0 required an update as b-trees were being generated on queries where sidx was
     * fixed, and the result was to be sorted by cs
     */
    @Override
    public void run()
            throws Exception
    {
        Connection c = _dbcw.getConnection();
        assert !c.getAutoCommit();
        Statement s = c.createStatement();
        try {
            // Drop the old inefficient/unused index cs0 from the db and replace it
            s.executeUpdate("drop index if exists " + T_CS + "0");
            CoreSchema.createIndexForCSTable(s);
        } finally {
            s.close();
        }
        c.commit();
    }
}
