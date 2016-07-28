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

import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * junit rule that tears down and creates a
 * MySQL database for use in unit tests.
 * <br>
 * Uses the methods defined in {@link com.aerofs.baseline.db.MySQLHelper},
 * and assumes that all actions are done on the local database with the following
 * credentials: {@code user:test pass:temp123}.
 * <br>
 * All parameters can be overridden by redefining the following
 * system properties (using -Djunit.mysqlUser=...):
 * <ul>
 *     <li>{@code junit.mysqlUser}: user that can create the mysql database</li>
 *     <li>{@code junit.mysqlPass}: password for the mysql user</li>
 *     <li>{@code junit.mysqlHost}: host on which the mysql database is located </li>
 * </ul>
 */
public final class MySQLDatabase extends ExternalResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(MySQLDatabase.class);

    // default local mysql parameters
    private static final String DEFAULT_LOCAL_MYSQL_USER = "test";
    private static final String DEFAULT_LOCAL_MYSQL_PASS = "temp123";
    private static final String DEFAULT_LOCAL_MYSQL_HOST = "localhost";

    // JUnit parameter names (allows defaults to be overridden via the test script)
    private static final String JUNIT_MYSQL_USER_PARAMETER = "junit.mysqlUser";
    private static final String JUNIT_MYSQL_PASS_PARAMETER = "junit.mysqlPass";
    private static final String JUNIT_MYSQL_HOST_PARAMETER = "junit.mysqlHost";

    // Cache these so the user can use them later.
    private final String mysqlUser;
    private final String mysqlPass;
    private final String mysqlHost;
    private final String mysqlDatabase;

    /**
     * Constructor.
     *
     * @param mysqlDatabase name of the database used in unit tests
     */
    public MySQLDatabase(String mysqlDatabase) {
        this.mysqlUser = getOrDefault(JUNIT_MYSQL_USER_PARAMETER, DEFAULT_LOCAL_MYSQL_USER);
        this.mysqlPass = getOrDefault(JUNIT_MYSQL_PASS_PARAMETER, DEFAULT_LOCAL_MYSQL_PASS);
        this.mysqlHost = getOrDefault(JUNIT_MYSQL_HOST_PARAMETER, DEFAULT_LOCAL_MYSQL_HOST);
        this.mysqlDatabase = mysqlDatabase;
    }

    private static String getOrDefault(String parameter, String defaultValue)
    {
        return System.getProperties().containsKey(parameter) ? System.getProperty(parameter) : defaultValue;
    }

    @Override
    protected void before() throws Throwable {
        super.before();

        try {
            MySQLHelper.resetDatabase(mysqlUser, mysqlHost, mysqlPass, mysqlDatabase);
        } catch (Exception e) {
            LOGGER.error("fail make database {}", mysqlDatabase, e);
            throw e;
        }
    }

    public void clear() {
        try {
            MySQLHelper.clearDatabase(mysqlUser, mysqlHost, mysqlPass, mysqlDatabase);
        } catch (Exception e) {
            LOGGER.error("fail clear database {}", mysqlDatabase, e);
        }
    }
}
