/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db.sql;

import com.aerofs.servlets.lib.db.ExDbInternal;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.google.common.base.Preconditions;

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
    public Connection getConnection() throws ExDbInternal
    {
        try {
            return getDataSource().getConnection();
        } catch (SQLException e) {
            throw new ExDbInternal(e);
        }
    }

    public DataSource getDataSource() throws ExDbInternal
    {
        // Must call init with a tomcat resource name before getting connections.
        Preconditions.checkNotNull(_dbResourceName);

        try {
            // The following is based on the example found here:
            // http://tomcat.apache.org/tomcat-6.0-doc/jndi-resources-howto.html#JDBC_Data_Sources
            //
            // N.B. all pooling parameters are pulled from the tomcat context (params include max
            // active connections, max idle connections, etc).

            Context initCtx = new InitialContext();
            Context envCtx = (Context) initCtx.lookup("java:comp/env");
            return (DataSource) envCtx.lookup(_dbResourceName);
        } catch (NamingException e) {
            // Turn NamingExceptions into ExDbInternals to prevent everyone from needing to throw
            // naming exceptions.
            throw new ExDbInternal(e);
        }
    }
}
