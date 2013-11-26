/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.update.DPUTUtil.IDatabaseOperation;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.lib.db.CoreSchema.C_ACL_ROLE;
import static com.aerofs.daemon.lib.db.CoreSchema.T_ACL;

/**
 * Move from discrete roles to more granular independent boolean flags
 *
 * VIEWER = 0 -> 0
 * EDITOR = 1 -> 1  = WRITE
 * OWNER  = 2 -> 3  = WRITE | MANAGE
 */
public class DPUTUpdateACLFromDiscreteRolesToFlags implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTUpdateACLFromDiscreteRolesToFlags(CoreDBCW dbcw)
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
                s.executeUpdate("update " + T_ACL
                        + " set " + C_ACL_ROLE + "=3"
                        + " where " + C_ACL_ROLE + "=2");
            }
        });
    }
}
