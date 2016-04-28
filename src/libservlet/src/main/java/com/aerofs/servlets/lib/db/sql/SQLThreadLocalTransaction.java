/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db.sql;

import com.aerofs.base.Loggers;
import com.aerofs.servlets.lib.db.AbstractThreadLocalTransaction;
import com.aerofs.servlets.lib.db.ExDbInternal;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SQLThreadLocalTransaction
    extends AbstractThreadLocalTransaction<SQLException>
    implements IDatabaseConnectionProvider<Connection>, IThreadLocalTransaction<SQLException>
{
    private static final Logger l = Loggers.getLogger(SQLThreadLocalTransaction.class);

    private IDatabaseConnectionProvider<Connection> _provider;
    private ThreadLocal<Connection> _connection = new ThreadLocal<Connection>();
    private ThreadLocal<List<Runnable>> _commitHooks = new ThreadLocal<>();

    @Override
    public boolean isInTransaction()
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
        _commitHooks.remove();
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
        try {
            List<Runnable> hooks = _commitHooks.get();
            if (hooks != null) hooks.forEach(Runnable::run);
        } finally {
            closeConnection();
        }
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

        // Log the rollback (do not throw) and close the connection so that the error that caused
        // the rollback and be propagated back to the caller, and so the connection is properly
        // cleaned up by the pooling mechanism.
        try {
            _connection.get().rollback();
        } catch (SQLException e) {
            l.error("Unable to rollback sql transaction. Possible broken sql object.");
        }

        closeConnection();
    }

    @Override
    public void cleanUp()
            throws SQLException
    {
        // Still in a transaction - did you forget to call commit?
        assert !isInTransaction();
    }

    public void onCommit(Runnable r) {
        List<Runnable> hooks = _commitHooks.get();
        if (hooks == null) {
            hooks = new ArrayList<>();
            _commitHooks.set(hooks);
        }
        hooks.add(r);
    }
}
