/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Class needed to facilitate database transactions and database connection creation.
 */
public abstract class AbstractSQLDatabase
{
    private final static Logger l = Util.l(AbstractSQLDatabase.class);

    private IDatabaseConnectionProvider<Connection> _provider;

    public AbstractSQLDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        _provider = provider;
    }

    // TODO use DBCW.throwOnConstraintViolation() instead
    protected static void throwOnConstraintViolation(SQLException e) throws ExAlreadyExist
    {
        if (isConstraintViolation(e)) throw new ExAlreadyExist(e);
    }

    protected static boolean isConstraintViolation(SQLException e)
    {
        return e.getMessage().startsWith("Duplicate entry");
    }

    protected final Connection getConnection()
            throws SQLException
    {
        Connection connection;

        try {
            connection = _provider.getConnection();
        } catch (ExDbInternal e) {
            throw new SQLException(e);
        }

        return connection;
    }

    protected PreparedStatement prepareStatement(String sql) throws SQLException
    {
        return getConnection().prepareStatement(sql);
    }

    protected static class ExSizeMismatch extends Exception
    {
        private static final long serialVersionUID = 0;

        ExSizeMismatch(String s) { super(s); }
    }

    /**
     * Same as executeBatch but simply log a warning on size mismatches instead of throwing an
     * ExSizeMismatch exception
     */
    protected static void executeBatchWarn(PreparedStatement ps, int batchSize,
            int expectedRowsAffectedPerBatchEntry)
            throws SQLException
    {
        try {
            executeBatch(ps, batchSize, expectedRowsAffectedPerBatchEntry);
        } catch (ExSizeMismatch e) {
            l.warn("Batch size mismatch", e);
        }
    }

    /**
     * Execute a batch DB update and check for size mismatch in the result
     */
    protected static void executeBatch(PreparedStatement ps, int batchSize,
            int expectedRowsAffectedPerBatchEntry)
            throws SQLException, ExSizeMismatch
    {
        int[] batchUpdates = ps.executeBatch();
        if (batchUpdates.length != batchSize) {
            throw new ExSizeMismatch("mismatch in batch size exp:" + batchSize + " act:"
                    + batchUpdates.length);
        }

        for (int rowsPerBatchEntry : batchUpdates) {
            if (rowsPerBatchEntry != expectedRowsAffectedPerBatchEntry) {
                throw new ExSizeMismatch("unexpected number of affected rows " +
                        "exp:" + expectedRowsAffectedPerBatchEntry + " act:" + rowsPerBatchEntry);
            }
        }
    }
}
