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

public class DPUTAddAggregateSyncColumn implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    DPUTAddAggregateSyncColumn(CoreDBCW dbcw)
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
                if (!_dbcw.columnExists(T_OA, C_OA_AG_SYNC)) {
                    s.executeUpdate("alter table " + T_OA +
                            " add column " + C_OA_AG_SYNC + " blob");
                }
            }
        });
    }
}
