/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

import com.aerofs.lib.LibParam;
import com.aerofs.servlets.lib.db.ExDbInternal;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.IDataSourceProvider;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.SQLException;

public class SpartaSQLConnectionProvider implements IDatabaseConnectionProvider<Connection>, IDataSourceProvider
{
    final DataSource _ds = migrated(dataSource());

    @Override
    public Connection getConnection() throws ExDbInternal
    {
        try {
            return _ds.getConnection();
        } catch (SQLException e) {
            throw new ExDbInternal(e);
        }
    }

    public DataSource getDataSource()
    {
        return _ds;
    }

    private static DataSource dataSource()
    {
        PoolProperties p = new PoolProperties();
        String databaseName = getDatabaseName();
        p.setUrl("jdbc:mysql://" + LibParam.MYSQL.MYSQL_ADDRESS + "/" + databaseName);
        p.setUsername(LibParam.MYSQL.MYSQL_USER);
        p.setPassword(LibParam.MYSQL.MYSQL_PASS);

        p.setDriverClassName(LibParam.MYSQL.MYSQL_DRIVER);
        p.setTestWhileIdle(false);
        p.setTestOnBorrow(true);
        p.setTestOnReturn(false);
        p.setValidationQuery("SELECT 1");
        p.setValidationQueryTimeout(30000);
        p.setMaxActive(8);
        p.setMinIdle(4);
        p.setLogAbandoned(true);
        p.setRemoveAbandoned(true);
        p.setRemoveAbandonedTimeout(30);
        p.setConnectionProperties(
                "cachePrepStmts=true; autoReconnect=true; " +
                "useUnicode=true; characterEncoding=utf8;");
        p.setJdbcInterceptors("ResetAbandonedTimer");

        return new DataSource(p);
    }

    private static DataSource migrated(DataSource ds)
    {
        // Perform database migration (with implicit initialization)
        Flyway flyway = new Flyway();
        flyway.setDataSource(ds);
        flyway.setValidateOnMigrate(false);
        flyway.setBaselineOnMigrate(true);
        flyway.setSchemas(getDatabaseName());
        flyway.migrate();
        return ds;
    }

    public static String getDatabaseName() {
        return "aerofs_sp";
    }
}
