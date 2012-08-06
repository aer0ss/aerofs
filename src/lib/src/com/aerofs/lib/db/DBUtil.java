package com.aerofs.lib.db;

import java.sql.SQLException;
import java.sql.Statement;

import com.aerofs.lib.Util;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.db.dbcw.MySQLDBCW;
import com.aerofs.lib.db.dbcw.SQLiteDBCW;

public class DBUtil
{
    public static void close(Statement s)
    {
        try {
            if (s != null) s.close();
        } catch (SQLException e) {
            Util.l(DBUtil.class).warn("cannot close stmt: " + e);
        }
    }

    public static IDBCW newDBCW(IDatabaseParams params)
    {
        if (params.isMySQL()) {
            return new MySQLDBCW(params.url(), params.autoCommit());
        } else {
            return new SQLiteDBCW(params.url(), params.autoCommit(),
                    params.sqliteExclusiveLocking(), params.sqliteWALMode());
        }
    }
}
