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
    public static String select(String table, String... fields)
    {
        return selectImpl(table, fields).toString();
    }

    /**
     * @return string "select <field>,<field> ... <field> from <table> where <condition>"
     */
    public static String selectWhere(String table, String condition, String... fields)
    {
        return selectImpl(table, fields).append(" where ").append(condition).toString();
    }

    private static StringBuilder selectImpl(String table, String... fields)
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

    /**
     * NOTE: if you're setting parameters in the updateParams using the .set functions of
     * PreparedStatement these parameters will have indecies _after_ the field indecies
     *
     * @param table table to insert into
     * @param updateParams parameters to update
     * @param fields fields to set
     * @return string "insert into table (field,field,field,...) (?,?,?,...) on duplicate key update
     *         updateParams"
     */
    public static String insertOnDuplicateUpdate(String table, String updateParams,
            String... fields)
    {
        return insertImpl(table, fields).append(" on duplicate key update ")
                .append(updateParams)
                .toString();
    }

    /**
     * @param table table to update
     * @param fields fields to update
     * @return string "insert into table (field,field,field,...) (?,?,?,...)"
     */
    public static String insert(String table, String... fields)
    {
        return insertImpl(table, fields).toString();
    }

    private static StringBuilder insertImpl(String table, String... fields)
    {
        StringBuilder sb = new StringBuilder("insert into ").append(table).append(" (");
        boolean first = true;
        for (String field : fields) {
            if (!first) sb.append(",");
            else first = false;
            sb.append(field);
        }
        sb.append(") VALUES(");

        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }

        sb.append(')');

        return sb;

    }

    /**
     * update all rows of the table, set the fields to the given fields
     *
     * @param table the table to be updated
     * @param fields the fields to be updated
     * @return string "update table set field1=?, field2=?, ...
     */
    public static String update(String table, String... fields)
    {
        return updateImpl(table, fields).toString();
    }

    /**
     * update all rows of the table, set the fields to the given fields where the condition matches
     * NOTE: when setting the parameters in the Prepared Statement, the condition paramaters are
     * indexed _after_ the field paramaters
     *
     * @param table the table to be updated
     * @param condition the fields to be updated
     * @param fields string "update table set field1=?, field2=?,... where condition
     */
    public static String updateWhere(String table, String condition, String... fields)
    {
        return updateImpl(table, fields).append(" where ").append(condition).toString();
    }

    /**
     * @return string "update <table> set <field>=?, <field>=2,...
     */
    private static StringBuilder updateImpl(String table, String... fields)
    {
        StringBuilder sb = new StringBuilder("update ").append(table).append(" set ");

        boolean first = true;
        for (String field : fields) {
            if (!first) sb.append(',');
            else first = false;
            sb.append(field).append("=?");
        }

        return sb;
    }

    /**
     * @return "delete from <table> where <field>=?, <field>=?,..."
     */
    public static String deleteWhereEquals(String table, String... fields)
    {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String field : fields) {
            if (!first) sb.append(" and ");
            else first = false;
            sb.append(field).append("=?");
        }

        return deleteWhere(table, sb.toString());
    }

    /**
     * @return "delete from <table> where <condition>"
     */
    public static String deleteWhere(String table, String condition)
    {
        return new StringBuilder("delete from ")
                .append(table)
                .append(" where ")
                .append(condition)
                .toString();
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

    // TODO (WW) use PreparedStatementWrapper instead
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
