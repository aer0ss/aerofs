package com.aerofs.servlets.lib.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Class to create direct jdbc connections rather than use connection pooling. This class is meant
 * for testing purposes only, because all of our production database connections should be pooled.
 */
public class JUnitDatabaseConnectionFactory implements IDatabaseConnectionProvider<Connection>
{
    private String _mysqlHost;
    private String _mysqlSchema;
    private String _mysqlUser;
    private String _mysqlPass;

    public JUnitDatabaseConnectionFactory(DatabaseParameters parameters)
    {
        // Initialize the factory for JUnit tests.
        this._mysqlHost = parameters.getMySQLHost();
        this._mysqlSchema = parameters.getMySQLDatabaseName();
        this._mysqlUser = parameters.getMySQLUser();
        this._mysqlPass = parameters.getMySQLPass();
    }

    @Override
    public Connection getConnection()
            throws ExDbInternal
    {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            return DriverManager.getConnection(
                    "jdbc:mysql://" + _mysqlHost + "/" + _mysqlSchema + "?user=" + _mysqlUser +
                            "&password=" + _mysqlPass +
                            "&autoReconnect=true&useUnicode=true&characterEncoding=utf8");
        }
        catch (ClassNotFoundException e) {
            // suppress ClassNotFoundExceptions to prevent callers from having to throw them too
            throw new ExDbInternal(e);
        } catch (SQLException e) {
            throw new ExDbInternal(e);
        }

    }
}
