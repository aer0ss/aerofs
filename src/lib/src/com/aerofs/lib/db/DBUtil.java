package com.aerofs.lib.db;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumSet;

import com.aerofs.lib.Util;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.db.dbcw.MySQLDBCW;
import com.aerofs.lib.db.dbcw.SQLiteDBCW;

public class DBUtil
{
    /**
     * @return string "select <field>,<field> ... <field> from <table>"
     */
    public static String selectFrom(String table, String... fields)
    {
        return selectFromImpl(table, fields).toString();
    }

    /**
     * @return string "select <field>,<field> ... <field> from <table> where <condition>"
     */
    public static String selectFromWhere(String table, String condition, String... fields)
    {
        return selectFromImpl(table, fields).append(" where ").append(condition).toString();
    }

    private static StringBuilder selectFromImpl(String table, String... fields)
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
            if (i > 0) sb.append(",");
            sb.append("?");
        }

        sb.append(")");

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
            if (!first) sb.append(",");
            else first = false;
            sb.append(field).append("=?");
        }

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


    /*
     * Helper code to encode and decode enum sets into database int fields
     * see: http://stackoverflow.com/questions/2199399/storing-enumset-in-a-database
     */
    public static <E extends Enum<E>> int encodeEnumSet(EnumSet<E> set)
    {
        int ret = 0;

        for (E val : set) {
            ret |= 1 << val.ordinal();
        }

        return ret;
    }

    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> EnumSet<E> decodeEnumSet(int code, Class<E> enumType)
    {
        try {
            E[] values = (E[]) enumType.getMethod("values").invoke(null);
            EnumSet<E> result = EnumSet.noneOf(enumType);
            while (code != 0) {
                int ordinal = Integer.numberOfTrailingZeros(code);
                code ^= Integer.lowestOneBit(code);
                result.add(values[ordinal]);
            }
            return result;
        } catch (IllegalAccessException ex) {
            // Shouldn't happen
            throw new RuntimeException(ex);
        } catch (InvocationTargetException ex) {
            // Probably a NullPointerException, caused by calling this method
            // from within E's initializer.
            throw (RuntimeException) ex.getCause();
        } catch (NoSuchMethodException ex) {
            // Shouldn't happen
            throw new RuntimeException(ex);
        }
    }
}
