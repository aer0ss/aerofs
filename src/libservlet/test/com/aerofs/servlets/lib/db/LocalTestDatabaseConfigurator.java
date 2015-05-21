/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

import com.aerofs.base.Loggers;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

public class LocalTestDatabaseConfigurator
{
    private static final Logger l = Loggers.getLogger(LocalTestDatabaseConfigurator.class);

    /**
     * Runs the given bash command.
     * @return the exit code of the command
     */
    private static int runBashCommand(String command)
            throws IOException, InterruptedException
    {
        Runtime runtime = Runtime.getRuntime();

        Process p = runtime.exec(new String[]{"/bin/bash", "-c", command});
        p.waitFor();
        l.info("command exitcode:" + p.exitValue());
        return p.exitValue();
    }

    public static void resetDB(DatabaseParameters params)
            throws IOException, InterruptedException
    {
        String reset = String.format(
                "%s/mysql -u%s -h%s -p%s -e \"delete from mysql.proc where db='%s' and " +
                        "type='PROCEDURE'; drop schema if exists %s; create database if not exists %s\"",
                params.getMySQLPath(), params.getMySQLUser(), params.getMySQLHost(), params.getMySQLPass(),
                params.getMySQLDatabaseName(), params.getMySQLDatabaseName(), params.getMySQLDatabaseName());

        l.info("setting up database schema");

        if (runBashCommand(reset) != 0) {
            throw new RuntimeException("failed to reset db (cmd: " + reset + ")");
        }
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
