package com.aerofs.lib.db;

import java.sql.SQLException;
import java.sql.Statement;

import com.aerofs.lib.Util;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.db.dbcw.MySQLDBCW;
import com.aerofs.lib.db.dbcw.SQLiteDBCW;

public class DBUtil
{
    /**
     * @return string "select <field>,<field> ... <field> from <table>"
     */
    public static String selectFrom(String table, String ...fields)
    {
        return selectFromImpl(table, fields).toString();
    }

    /**
     * @return string "select <field>,<field> ... <field> from <table> where <condition>"
     */
    public static String selectFromWhere(String table, String condition, String ...fields)
    {
        return selectFromImpl(table, fields).append(" where ").append(condition).toString();
    }

    private static StringBuilder selectFromImpl(String table, String ... fields)
    {
        StringBuilder sb = new StringBuilder("select ");
        boolean first = true;
        for (String field : fields) {
            if (!first) sb.append(',');
            else first = false;
            sb.append(field);
        }
        sb.append(" from ");
        sb.append(table);

        return sb;
    }

    public static String createUniqueIndex(String table, int indexNum, String ... columns)
    {
        return createIndexImpl(true, table, indexNum, columns);
    }

    public static String createIndex(String table, int indexNumber, String ... columns)
    {
        return createIndexImpl(false, table, indexNumber, columns);
    }

    private static String createIndexImpl(boolean isUniqueIndex, String table, int indexNumber,
            String ... columns)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("create ");
        if (isUniqueIndex) sb.append("unique ");
        sb.append("index " + table + indexNumber + " on " + table + "(");
        boolean first = true;
        for (String column : columns) {
            if (first) first = false;
            else sb.append(',');
            sb.append(column);
        }
        sb.append(')');
        return sb.toString();
    }

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
