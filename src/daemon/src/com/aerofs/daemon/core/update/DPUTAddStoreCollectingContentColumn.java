/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;

import static com.aerofs.daemon.lib.db.CoreSchema.C_STORE_COLLECTING_CONTENT;
import static com.aerofs.daemon.lib.db.CoreSchema.T_STORE;

public class DPUTAddStoreCollectingContentColumn implements IDaemonPostUpdateTask
{
    @Inject private IDBCW _dbcw;

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            if (!_dbcw.columnExists(T_STORE, C_STORE_COLLECTING_CONTENT)) {
                s.executeUpdate("alter table " + T_STORE
                        + " add column " + C_STORE_COLLECTING_CONTENT + _dbcw.boolType()
                        + " not null default 1");
            }
        });
    }
}
