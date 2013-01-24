/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db.sql;

import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.servlets.lib.db.ExDbInternal;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Class needed to facilitate database transactions and database connection creation.
 */
public abstract class AbstractSQLDatabase
{
    private IDatabaseConnectionProvider<Connection> _provider;

    // Not all subclasses need this. Added to here just for convenience.
    protected static final Calendar UTC_CALANDER =
            Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    public AbstractSQLDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        _provider = provider;
    }

    // TODO use DBCW.throwOnConstraintViolation() instead
    protected static void throwOnConstraintViolation(SQLException e, String errorMessage)
            throws ExAlreadyExist
    {
        if (isConstraintViolation(e)) throw new ExAlreadyExist(errorMessage);
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

    public static class ExBatchSizeMismatch extends SQLException
    {
        private static final long serialVersionUID = 0;

        ExBatchSizeMismatch(String s) { super(s); }
    }

    /**
     * Execute a batch DB update and check for size mismatch in the result
     */
    protected static void executeBatch(PreparedStatement ps, int batchSize,
            int expectedRowsAffectedPerBatchEntry)
            throws SQLException
    {
        int[] batchUpdates = ps.executeBatch();
        if (batchUpdates.length != batchSize) {
            throw new ExBatchSizeMismatch("mismatch in batch size exp:" + batchSize + " act:"
                    + batchUpdates.length);
        }

        for (int rowsPerBatchEntry : batchUpdates) {
            if (rowsPerBatchEntry != expectedRowsAffectedPerBatchEntry) {
                throw new ExBatchSizeMismatch("unexpected number of affected rows " +
                        "exp:" + expectedRowsAffectedPerBatchEntry + " act:" + rowsPerBatchEntry);
            }
        }
    }
}
