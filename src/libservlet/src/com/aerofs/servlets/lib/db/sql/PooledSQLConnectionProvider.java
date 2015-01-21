/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db.sql;

import com.aerofs.lib.LibParam;
import com.aerofs.servlets.lib.db.ExDbInternal;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.DataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class PooledSQLConnectionProvider implements IDatabaseConnectionProvider<Connection>
{
    final DataSource _ds = dataSource();

    @Override
    public Connection getConnection() throws ExDbInternal
    {
        try {
            return _ds.getConnection();
        } catch (SQLException e) {
            throw new ExDbInternal(e);
        }
    }

    private static DataSource dataSource()
    {
        PoolProperties p = new PoolProperties();
        p.setUrl("jdbc:mysql://" + LibParam.MYSQL.MYSQL_ADDRESS + "/aerofs_sp");
        p.setUsername(LibParam.MYSQL.MYSQL_USER);
        p.setPassword(LibParam.MYSQL.MYSQL_PASS);

        p.setDriverClassName(LibParam.MYSQL.MYSQL_DRIVER);
        p.setTestWhileIdle(false);
        p.setTestOnBorrow(true);
        p.setTestOnReturn(false);
        p.setValidationQuery("SELECT 1");
        p.setValidationQueryTimeout(30000);
        p.setMaxActive(8);
        p.setMaxIdle(4);
        p.setLogAbandoned(true);
        p.setRemoveAbandoned(true);
        p.setRemoveAbandonedTimeout(30);
        p.setConnectionProperties(
                "cachePrepStmts=true; autoReconnect=true; " +
                "useUnicode=true; characterEncoding=utf8;");

        return new DataSource(p);
    }

    public DataSource getDataSource()
    {
        return _ds;
    }
}
