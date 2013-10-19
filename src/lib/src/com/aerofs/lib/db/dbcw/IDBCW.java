package com.aerofs.lib.db.dbcw;

import java.sql.Connection;
import java.sql.SQLException;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExDBCorrupted;

/**
 * DBCW = Database Connection Wrapper. It provides abstractions independent to implementations of
 * JDBC-based databases.
 */
public interface IDBCW
{
    /**
     * Nop if init_() is already called.
     */
    public void init_() throws SQLException;

    /**
     * Nop if fini_() is already called.
     *
     * N.B. For graceful finialization, clients of this method should clean up all the prepared
     * statements associated with the connection.
     */
    public void fini_() throws SQLException;

    public void abort_();

    public void commit_() throws SQLException;

    public Connection getConnection() throws SQLException;

    public boolean isMySQL();

    /**
     * @throws ExAlreadyExist if e is a constraint violation (e.g. duplicate key) exception
     */
    public void throwOnConstraintViolation(SQLException e) throws ExAlreadyExist;

    public void throwIfDBCorrupted(SQLException e) throws ExDBCorrupted;

    public String insertOrIgnore();

    public String notNull();

    public String charSet();

    public String autoIncrement();

    public String chunkType();

    public String nameType();

    public String userIdType();

    /**
     * corresponding Java type: FID
     */
    public String fidType(int fidLen);

    /**
     * corresponding Java type: BloomFilter
     */
    public String bloomFilterType();

    /**
     * corresponding Java type: UniqueID
     */
    public String uniqueIdType();

    public String longType();

    public String boolType();

    /**
     * convert boolean to sql type (bool2sql)
     */
    public int b2s(boolean b);

    /**
     * corresponding Java type: Role
     */
    public String roleType();

    /**
     * Check whether a column exists
     * @param table Name of table to look into
     * @param column Name of column to look for
     * @return whether the given column exists in the given table
     */
    public boolean columnExists(String table, String column) throws SQLException;

    /**
     * Check whether a table exists
     * @param tableName Name of table to look for
     * @return whether the given table exists in the DB
     */
    public boolean tableExists(String tableName) throws SQLException;
}
