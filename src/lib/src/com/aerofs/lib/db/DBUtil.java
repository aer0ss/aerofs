package com.aerofs.lib.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.aerofs.base.Loggers;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.db.dbcw.MySQLDBCW;
import com.aerofs.lib.db.dbcw.SQLiteDBCW;
import com.google.common.base.Joiner;

import static com.google.common.base.Preconditions.checkState;

public class DBUtil
{
    /**
     * @return string "select <field>,<field> ... <field> from <table>"
     */
    public static String select(String table, String... fields)
    {
        return selectImpl("", table, fields).toString();
    }

    /**
     * @return string "select <field>,<field> ... <field> from <table> where <condition>"
     */
    public static String selectWhere(String table, String condition, String... fields)
    {
        return selectImpl("", table, fields).append(" where ").append(condition).toString();
    }

    /**
     * @return string "select distinct <field>,<field> ... <field> from <table> where <condition>"
     */
    public static String selectDistinctWhere(String table, String condition, String... fields)
    {
        return selectImpl("distinct ",table, fields).append(" where ").append(condition)
                .toString();
    }

    private static StringBuilder selectImpl(String prefix, String table, String... fields)
    {
        StringBuilder sb = new StringBuilder("select " + prefix);
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

    public static String orConditions(String... conditions)
    {
        return "(" + Joiner.on(" OR ").join(conditions) + ")";
    }

    public static String andConditions(String... conditions)
    {
        return "(" + Joiner.on(" AND ").join(conditions) + ")";
    }

    /**
     * NOTE: if you're setting parameters in the updateParams using the .set functions of
     * PreparedStatement these parameters will have indices _after_ the field indices.
     *
     * Use insertedOrUpdatedOneRow() to check that this query affected exactly one row.
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
     * Checks that the result of a insertOnDuplicateUpdate() query successfully inserted or updated
     * exactly one row.
     */
    public static boolean insertedOrUpdatedOneRow(int result)
    {
        /*
         * The "INSERT ... ON DUPLICATE KEY UPDATE" function returns 1 for every succesful INSERT
         * and 2 for every succesful UPDATE. That means that if you do the command on 5 rows,
         * 3 of which result in INSERT, and 2 of which result in UPDATE, the return value
         * will be 7 (3*1 + 2*2). In our case, we expect either a single UPDATE, or a single
         * INSERT, so a return value of 1 or 2 is acceptable.
         *
         * See http://bugs.mysql.com/bug.php?id=2709 for more information
         */
        return (result == 1 || result == 2);
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
        return "delete from " + table + " where " + condition;
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
        sb.append("index ")
                .append(table)
                .append(indexNumber)
                .append(" on ")
                .append(table)
                .append("(");
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
            Loggers.getLogger(DBUtil.class).warn("cannot close stmt: " + e);
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

    /**
     * This method examines the result for "select count(*)" queries, asserts the result is either
     * zero or one, and return true if the count is one. See count() for example usage.
     */
    public static boolean binaryCount(ResultSet rs)
            throws SQLException
    {
        int count = count(rs);
        assert count == 0 || count == 1;
        return count == 1;
    }

    /**
     * This method returns the result of "select count(*)" queries. Example:
     *
     *  ResultSet rs = ps.executeQuery();
     *  try {
     *      return count(rs);
     *  } finally {
     *      rs.close();
     *  }
     */
    public static int count(ResultSet rs)
            throws SQLException
    {
        Util.verify(rs.next());
        int count = rs.getInt(1);
        assert !rs.next();
        return count;
    }

    public static long generatedId(PreparedStatement ps) throws SQLException
    {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            checkState(rs.next());
            return rs.getLong(1);
        }
    }
}
