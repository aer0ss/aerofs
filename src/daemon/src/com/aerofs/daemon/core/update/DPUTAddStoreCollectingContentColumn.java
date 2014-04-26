/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.update.DPUTUtil.IDatabaseOperation;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.lib.db.CoreSchema.C_STORE_COLLECTING_CONTENT;
import static com.aerofs.daemon.lib.db.CoreSchema.T_STORE;

public class DPUTAddStoreCollectingContentColumn implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTAddStoreCollectingContentColumn(CoreDBCW dbcw)
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
                if (!_dbcw.columnExists(T_STORE, C_STORE_COLLECTING_CONTENT)) {
                    s.executeUpdate("alter table " + T_STORE
                            + " add column " + C_STORE_COLLECTING_CONTENT + _dbcw.boolType()
                            + " not null default 1");
                }
            }
        });
    }
}
