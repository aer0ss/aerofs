/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.dbcw.IDBCW;

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
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            if (!_dbcw.columnExists(T_STORE, C_STORE_NAME)) {
                s.executeUpdate("alter table " + T_STORE
                        + " add column " + C_STORE_NAME + _dbcw.nameType());
            }
        });
    }
}
