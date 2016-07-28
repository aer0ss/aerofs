/*
 * Copyright 2015 Air Computing Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.baseline.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;

/**
 * Helper functions to invoke the MySQL command-line and
 * create a UTF-8 MySQL database for unit tests.
 */
public abstract class MySQLHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(MySQLHelper.class);

    static {
        try {
            Class.forName("com.mysql.jdbc.Driver").asSubclass(Driver.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Tear down and create a MySQL database for use in unit tests.
     * <br>
     * The newly-created database has the following properties:
     * <ul>
     *     <li>Encoding: utf8mb4 (supports full unicode)</li>
     *     <li>Collation: utf8mb4_bin</li>
     * </ul>
     *
     * @param mysqlUser MySQL user that can create the {@code mysqlDatabase}
     * @param mysqlHost host on which the MySQL database exists
     * @param mysqlPass password for {@code mysqlUser}
     * @param mysqlDatabase database used in unit tests
     *
     * @throws Exception if {@code mysqlDatabase} cannot be torn down and re-created
     */
    public static void resetDatabase(String mysqlUser, String mysqlHost, String mysqlPass, String mysqlDatabase) throws Exception {
        LOGGER.info("setting up database schema");
        Connection c = DriverManager.getConnection("jdbc:mysql://" + mysqlHost, mysqlUser, mysqlPass);
        c.setAutoCommit(true);
        try (Statement s = c.createStatement()) {
            s.executeUpdate("drop database if exists " + mysqlDatabase);
            s.executeUpdate("create database " + mysqlDatabase);
        }
        c.close();
    }

    /**
     * Clear all data from the tables in MySQL database.
     * <br>
     *
     * @param mysqlUser MySQL user that can create the {@code mysqlDatabase}
     * @param mysqlHost host on which the MySQL database exists
     * @param mysqlPass password for {@code mysqlUser}
     * @param mysqlDatabase database used in unit tests
     *
     * @throws Exception if {@code mysqlDatabase} cannot be torn down and re-created
     */
    public static void clearDatabase(String mysqlUser, String mysqlHost, String mysqlPass, String mysqlDatabase) throws Exception {
        Connection c = DriverManager.getConnection("jdbc:mysql://" + mysqlHost + "/" + mysqlDatabase, mysqlUser, mysqlPass);
        c.setAutoCommit(true);
        DatabaseMetaData md = c.getMetaData();
        try (ResultSet rs = md.getTables(null, null, "%", null)) {
            while (rs.next()) {
                String table = rs.getString(3);
                try (Statement s = c.createStatement()) {
                    s.executeUpdate("truncate table " + table);
                }
            }
        }
        c.close();
    }

    private static String getMysqlPassParam(String mysqlPass)
    {
        return mysqlPass.isEmpty() ? "" : String.format("-p%s", mysqlPass);
    }

    private MySQLHelper() {
        // to prevent instantiation
    }
}
