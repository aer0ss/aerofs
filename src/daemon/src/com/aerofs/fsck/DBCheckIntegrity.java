/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.fsck;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.google.inject.Inject;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.InOutArg;
import com.aerofs.lib.db.dbcw.IDBCW;

import static com.aerofs.fsck.DBChecker.error;

public class DBCheckIntegrity
{
    private final IDBCW _dbcw;

    @Inject
    public DBCheckIntegrity(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    public void check_(InOutArg<Boolean> okay) throws SQLException
    {
        Statement stmt = _dbcw.getConnection().createStatement();
        try {
            ResultSet rs = stmt.executeQuery("pragma integrity_check");
            try {
                while (rs.next()) {
                    String result = rs.getString(1);
                    if (!result.equals("ok")) error("integrity", result, okay);
                }
            } finally {
                rs.close();
            }
        } finally {
            stmt.close();
        }
    }
}
