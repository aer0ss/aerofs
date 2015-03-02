/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.cache;

import com.aerofs.daemon.lib.db.ISchema;
import com.aerofs.lib.db.TableDumper;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.lib.db.DBUtil.createIndex;

public class CacheSchema implements ISchema
{
    public static final String
            T_BlockCache            = "bsca",
            C_BlockCache_Hash       = "bsca_hash",
            C_BlockCache_Time       = "bsca_time";

    @Override
    public void create_(Statement s, IDBCW dbcw) throws SQLException
    {
        s.execute("create table if not exists " + T_BlockCache + "( " +
                C_BlockCache_Hash + " blob not null primary key," +
                C_BlockCache_Time + dbcw.longType() + " not null )" +
                dbcw.charSet());
        s.executeUpdate(createIndex(T_BlockCache, 0, C_BlockCache_Time));
    }

    @Override
    public void dump_(Statement s, PrintStream ps) throws IOException, SQLException
    {
        TableDumper td = new TableDumper(new PrintWriter(ps));
        td.dumpTable(s, T_BlockCache);
    }
}
