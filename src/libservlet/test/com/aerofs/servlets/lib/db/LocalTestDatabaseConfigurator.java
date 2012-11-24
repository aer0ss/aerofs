/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

import com.aerofs.lib.Util;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

public class LocalTestDatabaseConfigurator
{
    private static final Logger l = Util.l(LocalTestDatabaseConfigurator.class);

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

    /**
     * Sets up a local MySQL database based on the given JUnitDatabaseParameters instance.
     */
    public static void initializeLocalDatabase(DatabaseParameters params)
            throws SQLException, ClassNotFoundException, InterruptedException, IOException
    {
        String dropProcedures = String.format(
                 "%s/mysql -u%s -h%s -p%s -e \"delete from mysql.proc where db='%s' and " +
                         "type='PROCEDURE'\"",
                params.getMySQLPath(), params.getMySQLUser(), params.getMySQLHost(),
                params.getMySQLPass(), params.getMySQLDatabaseName());

        String dropDatabase = String.format("%s/mysql -u%s -h%s -p%s -e 'drop schema if exists %s'",
                params.getMySQLPath(), params.getMySQLUser(), params.getMySQLHost(),
                params.getMySQLPass(), params.getMySQLDatabaseName());

        String createDatabase = String.format(
                "%s/mysql -u%s -h%s -p%s -e 'create database if not exists %s'",
                params.getMySQLPath(), params.getMySQLUser(), params.getMySQLHost(),
                params.getMySQLPass(), params.getMySQLDatabaseName());

        String loadSchema = String.format(
                "%s/mysql -u%s -h%s -p%s %s < %s/%s",
                params.getMySQLPath(), params.getMySQLUser(), params.getMySQLHost(),
                params.getMySQLPass(), params.getMySQLDatabaseName(), params.getMySQLSchemaPath(),
                params.getMySQLSchemaName());

        l.info("setting up database schema");

        if (runBashCommand(dropProcedures) != 0) {
            throw new RuntimeException("failed to drop pr (cmd: " + dropProcedures + ")");
        }

        if (runBashCommand(dropDatabase) != 0) {
            throw new RuntimeException("failed to drop db (cmd: " + dropDatabase + ")");
        }

        if (runBashCommand(createDatabase) != 0) {
            throw new RuntimeException("failed to make db (cmd: " + createDatabase  + ")");
        }

        if (runBashCommand(loadSchema) != 0) {
            throw new RuntimeException("failed to load schema (cmd: " + loadSchema + ")");
        }
    }
}
