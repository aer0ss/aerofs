/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.umdc;

import static com.aerofs.daemon.lib.db.CoreSchema.*;
import static com.aerofs.lib.db.DBUtil.select;
import static com.aerofs.lib.db.DBUtil.updateWhere;

import com.aerofs.lib.Util;
import com.aerofs.lib.db.dbcw.IDBCW;

import javax.annotation.Nonnull;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ObfuscatedDatabase
{
    private IDBCW _db;

    public ObfuscatedDatabase(@Nonnull IDBCW db)
    {
        _db = db;
    }

    public void init_() throws SQLException
    {
        _db.init_();
    }

    public void finish_() throws SQLException
    {
        _db.fini_();
    }

    public void obfuscate() throws SQLException
    {
        Statement s = _db.getConnection().createStatement();
        String sql = updateWhere(T_OA, C_OA_SIDX + "=? AND " + C_OA_OID + "=?", C_OA_NAME);
        PreparedStatement ps = _db.getConnection().prepareStatement(sql);
        try {
            sql = select(T_OA, C_OA_SIDX, C_OA_OID, C_OA_NAME);
            ResultSet rs = s.executeQuery(sql);
            while (rs.next()) {
                int soid = rs.getInt(1);
                byte[] oid = rs.getBytes(2);
                String name = rs.getString(3);
                String obfuscatedName = Util.crc32(name);

                ps.setString(1, obfuscatedName);
                ps.setInt(2, soid);
                ps.setBytes(3, oid);

                ps.execute();
            }
        } finally {
            s.close();
            ps.close();
        }
    }

    public void pruneTables(String ... tableNames) throws SQLException
    {
        if (tableNames == null) return;

        Statement s = _db.getConnection().createStatement();
        try {
            for (String t : tableNames) {
                s.execute("drop table " + t);
            }
        } finally {
            s.close();
        }
    }

    public void vacuum() throws SQLException
    {
        Statement s = _db.getConnection().createStatement();
        try {
            s.execute("vacuum");
        } finally {
            s.close();
        }
    }
}
