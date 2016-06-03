/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;

import java.sql.*;

public class LocalTestDatabaseConfigurator
{
    public static void resetDB(DatabaseParameters params) throws SQLException {
        try {
            Class.forName("com.mysql.jdbc.Driver").asSubclass(Driver.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        Connection c = DriverManager.getConnection("jdbc:mysql://" + params.getMySQLHost(),
                params.getMySQLUser(),
                params.getMySQLPass());
        c.setAutoCommit(true);
        try (Statement s = c.createStatement()) {
            s.executeUpdate("drop database if exists " + params.getMySQLDatabaseName());
            s.executeUpdate("create database " + params.getMySQLDatabaseName());
        }
        c.close();
    }

    /**
     * Sets up a local MySQL database based on the given JUnitDatabaseParameters instance.
     *
     * TODO: in-memory db would be nice...
     */
    public static void initializeLocalDatabase(DatabaseParameters params)
            throws SQLException {
        Flyway flyway = new Flyway();
        flyway.setDataSource("jdbc:mysql://" + params.getMySQLHost() + "/" + params.getMySQLDatabaseName(),
                params.getMySQLUser(), params.getMySQLPass());
        flyway.setBaselineOnMigrate(true);
        flyway.setValidateOnMigrate(true);
        flyway.setLocations("filesystem:" + params.getMySQLSchemaPath());
        flyway.setSchemas(params.getMySQLDatabaseName());

        MigrationInfoService svc = flyway.info();
        if (svc.current().getState().isResolved()) {
            try {
                flyway.migrate();
                return;
            } catch (Throwable e) {
            }
        } else {
            System.out.println("future migration detected: " + svc.current().getVersion().getVersion());
        }

        resetDB(params);
        flyway.migrate();
    }
}
