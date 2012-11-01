/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

import java.sql.Connection;
import java.sql.SQLException;

public class SQLThreadLocalTransaction
    extends AbstractThreadLocalTransaction<SQLException>
    implements
        IDatabaseConnectionProvider<Connection>,
        IThreadLocalTransaction<SQLException>
{
    private IDatabaseConnectionProvider<Connection> _provider;
    private ThreadLocal<Connection> _connection = new ThreadLocal<Connection>();

    @Override
    protected boolean isInTransaction()
    {
        return _connection.get() != null;
    }

    private void getConnectionFromProvider()
            throws SQLException
    {
        Connection connnection;

        try {
            connnection = _provider.getConnection();
        } catch (ExDbInternal e) {
            throw new SQLException(e);
        }

        // All connections have autocommit disabled.
        connnection.setAutoCommit(false);

        _connection.set(connnection);
    }

    /**
     * "Close" the pooled connection; that is, return the connection to the connection pool.
     */
    protected void closeConnection()
            throws SQLException
    {
        if (_connection.get() != null) {
            _connection.get().close();
            _connection.remove();
        }
    }

    /**
     * Initialize a new ThreadLocalTransaction with the given connection provider. This object
     * should be dependency injected into any classes that need access to a database connection.
     */
    public SQLThreadLocalTransaction(IDatabaseConnectionProvider<Connection> provider)
    {
        _provider = provider;
    }

    /**
     * Gets a connection to the database in the context of the current transaction if in an ongoing
     * transaction or from the connection pool if not in an ongoing transaction.
     */
    @Override
    public Connection getConnection()
    {
        // Must be in a transaction to get a connection.
        assert isInTransaction();
        return _connection.get();
    }

    @Override
    public void begin()
            throws SQLException
    {
        // Already in a transaction.
        assert !isInTransaction();
        getConnectionFromProvider();
    }

    @Override
    public void commit()
            throws SQLException
    {
        // Must be in a transaction to commit.
        assert isInTransaction();

        _connection.get().commit();
        closeConnection();
    }

    @Override
    public void handleException()
    {
        handleExceptionHelper();
    }

    @Override
    public void rollback()
            throws SQLException
    {
        assert isInTransaction();

        _connection.get().rollback();
        closeConnection();
    }

    @Override
    public void cleanUp()
            throws SQLException
    {
        if (isInTransaction()) {
            rollback();

            // Still in a transaction - did you forget to call commit?
            assert false;
        }
    }
}
