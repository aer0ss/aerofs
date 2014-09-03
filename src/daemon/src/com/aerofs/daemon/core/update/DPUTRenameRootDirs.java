/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.id.OID;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

import java.sql.PreparedStatement;

/**
 * Store root directories used to be named R which prevented object with that name from being
 * created directly at the root of a store
 */
public class DPUTRenameRootDirs implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTRenameRootDirs(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            PreparedStatement ps = s.getConnection().prepareStatement(DBUtil.updateWhere(T_OA,
                    C_OA_OID + "=?", C_OA_NAME));
            ps.setString(1, "");
            ps.setBytes(2, OID.ROOT.getBytes());
            ps.executeUpdate();
        });
    }
}
