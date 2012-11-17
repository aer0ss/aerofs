/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.cache;

import com.aerofs.lib.db.TableDumper;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.lib.db.DBUtil.createIndex;

public class CacheSchema
{
    public static final String
            T_BlockCache            = "bsca",
            C_BlockCache_Hash       = "bsca_hash",
            C_BlockCache_Time       = "bsca_time";

    private final IDBCW _dbcw;

    public CacheSchema(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    public void create_() throws SQLException
    {
        Connection c = _dbcw.getConnection();
        Statement s = c.createStatement();

        try {
            s.execute("create table if not exists " + T_BlockCache + "( " +
                    C_BlockCache_Hash + " blob not null primary key," +
                    C_BlockCache_Time + _dbcw.longType() + " not null )" +
                    _dbcw.charSet());
            s.executeUpdate(createIndex(T_BlockCache, 0, C_BlockCache_Time));
        } finally {
            s.close();
        }

        c.commit();
    }

    public void dump_(PrintWriter pw) throws IOException, SQLException
    {
        Connection c = _dbcw.getConnection();
        Statement s = c.createStatement();
        try {
            TableDumper td = new TableDumper(pw);
            td.dumpTable(s, T_BlockCache);
        } finally {
            s.close();
        }
    }
}
