/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 * Aliasing used to have a bug where an explicitly expelled object would remain in the expelled
 * object table after being removed from the OA table during aliasing which would lead to puzzling
 * assert failures in HdListExpelledObjects
 *
 * NB: we do not replace the source by its target as the old Aliasing code would not propagate the
 * expelled flag to the target hence adding the target to the table of expelled objects would break
 * invariants in the code.
 */
public class DPUTFixExpelledAlias implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTFixExpelledAlias(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            // select all SOIDs which are:
            // 1. "source" aka "alias" of an alias relation
            // 2. present in the table of expelled objects
            ResultSet rs = s.executeQuery("select "
                    + C_ALIAS_SIDX + "," + C_ALIAS_SOURCE_OID
                    + " from " + T_ALIAS
                    + " inner join " + T_EX
                    + " on " + C_ALIAS_SIDX + "=" + C_EX_SIDX
                    + " and " + C_ALIAS_SOURCE_OID + "=" + C_EX_OID
                    );

            // remove these SOIDs in batch from the table of expelled objects
            PreparedStatement ps = s.getConnection().prepareStatement("delete from " + T_EX
                    + " where " + C_EX_SIDX + "=? and " + C_EX_OID + "=?");

            try {
                while (rs.next()) {
                    ps.setInt(1, rs.getInt(1));
                    ps.setBytes(2, rs.getBytes(2));
                    ps.addBatch();
                }
            } finally {
                rs.close();
            }

            ps.executeBatch();
        });
    }
}
