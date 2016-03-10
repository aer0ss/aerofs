/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.flywaydb.core.Flyway;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class LocalTestDatabaseConfigurator
{
    public static void resetDB(DatabaseParameters params)
            throws SQLException
    {
        String db = params.getMySQLDatabaseName();

        PoolProperties p = new PoolProperties();
        p.setUrl("jdbc:mysql://" + params.getMySQLHost() + "/" + db);
        p.setUsername(params.getMySQLUser());
        p.setPassword(params.getMySQLPass());
        p.setDriverClassName("com.mysql.jdbc.Driver");

        Connection c = new DataSource(p).getConnection();
        c.setAutoCommit(true);
        try (Statement s = c.createStatement()) {
            s.executeUpdate("drop database if exists " + db);
            s.executeUpdate("create database " + db);
        }
        c.close();
    }

    /**
     * Sets up a local MySQL database based on the given JUnitDatabaseParameters instance.
     *
     * TODO: in-memory db would be nice...
     */
    public static void initializeLocalDatabase(DatabaseParameters params)
            throws SQLException, ClassNotFoundException, InterruptedException, IOException {
        Flyway flyway = new Flyway();
        flyway.setDataSource("jdbc:mysql://" + params.getMySQLHost() + "/" + params.getMySQLDatabaseName(),
                params.getMySQLUser(), params.getMySQLPass());
        flyway.setBaselineOnMigrate(true);
        flyway.setValidateOnMigrate(false);
        flyway.setLocations("filesystem:" + params.getMySQLSchemaPath());
        flyway.setSchemas(params.getMySQLDatabaseName());

        try {
            flyway.migrate();
            return;
        } catch (Throwable e) {}

        resetDB(params);
        flyway.migrate();
    }
}
