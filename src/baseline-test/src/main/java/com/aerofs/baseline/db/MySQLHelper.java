/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.baseline.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper functions to invoke the MySQL command-line and
 * create a UTF-8 MySQL database for unit tests.
 */
public abstract class MySQLHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(MySQLHelper.class);

    /**
     * Tear down and create a MySQL database for use in unit tests.
     * <br>
     * The newly-created database has the following properties:
     * <ul>
     *     <li>Encoding: utf8mb4 (supports full unicode)</li>
     *     <li>Collation: utf8mb4_bin</li>
     * </ul>
     *
     * @param mysqlPath absolute path to the MySQL binary
     * @param mysqlUser MySQL user that can create the {@code mysqlDatabase}
     * @param mysqlHost host on which the MySQL database exists
     * @param mysqlPass password for {@code mysqlUser}
     * @param mysqlDatabase database used in unit tests
     *
     * @throws Exception if {@code mysqlDatabase} cannot be torn down and re-created
     *
     * @see com.aerofs.baseline.db.MySQLHelper#makeDatabase
     * @see com.aerofs.baseline.db.MySQLHelper#dropDatabase
     */
    public static void resetDatabase(String mysqlPath, String mysqlUser, String mysqlHost, String mysqlPass, String mysqlDatabase) throws Exception {
        LOGGER.info("setting up database schema");
        dropDatabase(mysqlPath, mysqlUser, mysqlHost, mysqlPass, mysqlDatabase);
        makeDatabase(mysqlPath, mysqlUser, mysqlHost, mysqlPass, mysqlDatabase);
    }

    /**
     * Create a MySQL database for use in unit tests.
     * <br>
     * The newly-created database has the following properties:
     * <ul>
     *     <li>Encoding: utf8mb4 (supports full unicode)</li>
     *     <li>Collation: utf8mb4_bin</li>
     * </ul>
     *
     * @param mysqlPath absolute path to the MySQL binary
     * @param mysqlUser MySQL user that can create the {@code mysqlDatabase}
     * @param mysqlHost host on which the MySQL database exists
     * @param mysqlPass password for {@code mysqlUser}
     * @param mysqlDatabase database used in unit tests
     *
     * @throws Exception if {@code mysqlDatabase} cannot be created
     */
    public static void makeDatabase(String mysqlPath, String mysqlUser, String mysqlHost, String mysqlPass, String mysqlDatabase) throws Exception {
        String mysqlCommand = String.format("%s/mysql -u%s -h%s -p%s -e 'create database if not exists %s default character set utf8mb4 collate utf8mb4_bin'", mysqlPath, mysqlUser, mysqlHost, mysqlPass, mysqlDatabase);
        if (runBashCommand(mysqlCommand) != 0) {
            throw new RuntimeException("failed to make db (cmd: " + mysqlCommand  + ")");
        }
    }

    /**
     * Tear down a MySQL database.
     * <br>
     * Deletes the database and any associated stored procedures.
     *
     * @param mysqlPath absolute path to the MySQL binary
     * @param mysqlUser MySQL user that can create the {@code mysqlDatabase}
     * @param mysqlHost host on which the MySQL database exists
     * @param mysqlPass password for {@code mysqlUser}
     * @param mysqlDatabase database used in unit tests
     *
     * @throws Exception if {@code mysqlDatabase} cannot be torn down and re-created
     */
    public static void dropDatabase(String mysqlPath, String mysqlUser, String mysqlHost, String mysqlPass, String mysqlDatabase) throws Exception {
        String mysqlCommand;

        mysqlCommand = String.format("%s/mysql -u%s -h%s -p%s -e \"delete from mysql.proc where db='%s' and type='PROCEDURE'\"", mysqlPath, mysqlUser, mysqlHost, mysqlPass, mysqlDatabase);
        if (runBashCommand(mysqlCommand) != 0) {
            throw new RuntimeException("failed to drop pr (cmd: " + mysqlCommand + ")");
        }

        mysqlCommand = String.format("%s/mysql -u%s -h%s -p%s -e 'drop schema if exists %s'", mysqlPath, mysqlUser, mysqlHost, mysqlPass, mysqlDatabase);
        if (runBashCommand(mysqlCommand) != 0) {
            throw new RuntimeException("failed to drop db (cmd: " + mysqlCommand + ")");
        }
    }

    // runs the given bash command and returns the exit code
    private static int runBashCommand(String command) throws Exception {
        Runtime runtime = Runtime.getRuntime();

        Process p = runtime.exec(new String[]{"/bin/bash", "-c", command});
        p.waitFor();

        LOGGER.trace("command exit code:{}", p.exitValue());

        return p.exitValue();
    }

    private MySQLHelper() {
        // to prevent instantiation
    }
}
