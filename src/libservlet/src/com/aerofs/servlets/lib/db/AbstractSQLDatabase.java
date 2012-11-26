/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

import com.aerofs.lib.ex.ExAlreadyExist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Class needed to facilitate database transactions and database connection creation.
 */
public abstract class AbstractSQLDatabase
{
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

}
