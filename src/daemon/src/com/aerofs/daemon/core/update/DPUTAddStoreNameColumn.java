/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.update.DPUTUtil.IDatabaseOperation;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

public class DPUTAddStoreNameColumn implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTAddStoreNameColumn(CoreDBCW dbcw)
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
                if (!_dbcw.columnExists(T_STORE, C_STORE_NAME)) {
                    s.executeUpdate("alter table " + T_STORE
                            + " add column " + C_STORE_NAME + _dbcw.nameType());
                }
            }
        });
    }
}
