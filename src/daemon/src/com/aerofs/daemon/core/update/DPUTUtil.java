/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DPUTUtil
{
    public static interface IDatabaseOperation
    {
        void run_(Statement s)
                throws SQLException;
    }

    /**
     * This method calls op.run_() in a single database transaction. The transaction is committed
     * only if run_() returns without throwing.
     */
    static public void runDatabaseOperationAtomically_(IDBCW dbcw, IDatabaseOperation op)
            throws SQLException
    {
        Connection c = dbcw.getConnection();
        assert !c.getAutoCommit();
        Statement s = c.createStatement();
        try {
            op.run_(s);
        } finally {
            s.close();
        }

        c.commit();
    }
}
