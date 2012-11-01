/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class PooledSQLConnectionProvider implements IDatabaseConnectionProvider<Connection>
{
    String _dbResourceName = null;

    public void init_(String dbResourceName)
    {
        _dbResourceName = dbResourceName;
    }

    @Override
    public Connection getConnection()
            throws ExDbInternal
    {
        // Must call init with a tomcat resource name before getting connections.
        assert _dbResourceName != null;

        try {
            // The following is based on the example found here:
            // http://tomcat.apache.org/tomcat-6.0-doc/jndi-resources-howto.html#JDBC_Data_Sources
            //
            // N.B. all pooling parameters are pulled from the tomcat context (params include max
            // active connections, max idle connections, etc).

            Context initCtx = new InitialContext();
            Context envCtx = (Context) initCtx.lookup("java:comp/env");
            DataSource ds = (DataSource) envCtx.lookup(_dbResourceName);

            return ds.getConnection();
        }
        catch (NamingException e) {
            // Turn NamingExceptions into ExDbInternals to prevent everyone from needing to throw
            // naming exceptions.
            throw new ExDbInternal();
        } catch (SQLException e) {
            throw new ExDbInternal();
        }
    }
}
