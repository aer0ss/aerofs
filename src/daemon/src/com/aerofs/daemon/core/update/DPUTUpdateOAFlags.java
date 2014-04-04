/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.update.DPUTUtil.IDatabaseOperation;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 *
 */
public class DPUTUpdateOAFlags implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTUpdateOAFlags(CoreDBCW dbcw)
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
                // discard old FLAG_EXPELLED_INH
                // update value of FLAG_EXPELLED_ORG
                s.executeUpdate("update " + T_OA
                        + " set "
                        + C_OA_FLAGS + "=(" + C_OA_FLAGS + " >> 1) & 1"
                        );
            }
        });
    }
}
