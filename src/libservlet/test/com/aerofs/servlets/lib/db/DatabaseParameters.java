package com.aerofs.servlets.lib.db;

import java.io.File;
import java.sql.Connection;

/**
 * Abstract class that is reponsible for parsing junit env variables and providing them to the
 * user of this class.
 */
public abstract class DatabaseParameters
{
    // Default local mysql parameters
    private static final String DEFAULT_LOCAL_MYSQL_USER = "test";
    private static final String DEFAULT_LOCAL_MYSQL_PASS = "temp123";
    private static final String DEFAULT_LOCAL_MYSQL_HOST = "localhost";
    private static final String DEFAULT_LOCAL_MYSQL_PATH = "/usr/local/bin"; // homebrew default

    // JUnit parameter names (allows defaults to be overriden via the test script)
    private static final String JUNIT_MYSQL_USER_PARAMETER = "junit.mysqlUser";
    private static final String JUNIT_MYSQL_PASS_PARAMETER = "junit.mysqlPass";
    private static final String JUNIT_MYSQL_HOST_PARAMETER = "junit.mysqlHost";
    private static final String JUNIT_MYSQL_PATH_PARAMETER = "junit.mysqlPath";

    // Cache these so the user can use them later.
    private final String _mysqlPath;
    private final String _mysqlUser;
    private final String _mysqlPass;
    private final String _mysqlHost;

    public DatabaseParameters()
    {
        _mysqlPath = getOrDefault(JUNIT_MYSQL_PATH_PARAMETER, DEFAULT_LOCAL_MYSQL_PATH);
        _mysqlUser = getOrDefault(JUNIT_MYSQL_USER_PARAMETER, DEFAULT_LOCAL_MYSQL_USER);
        _mysqlPass = getOrDefault(JUNIT_MYSQL_PASS_PARAMETER, DEFAULT_LOCAL_MYSQL_PASS);
        _mysqlHost = getOrDefault(JUNIT_MYSQL_HOST_PARAMETER, DEFAULT_LOCAL_MYSQL_HOST);
    }

    /**
     * Given an ordered list of places that could be directories, return the first one that
     * matches. Why? So we can run tests from IDEA without having to set the default CWD
     * to something that disagrees with the ant default CWD.
     * TL;DR: because Jon got annoyed.
     */
    protected String findExistingPath(String[] paths)
    {
        for (String p : paths) {
            File f = new File(p);
            if (f.exists() && f.isDirectory()) return p;
        }
        return paths[0];
    }

    /**
     * Get a JUnitDatabaseConnectionFactory for this set of parameters.
     */
    public IDatabaseConnectionProvider<Connection> getProvider()
    {
        return new JUnitDatabaseConnectionFactory(this);
    }

    public static String getOrDefault(String parameter, String defaultValue)
    {
        return System.getProperties().containsKey(parameter) ?
                System.getProperty(parameter) : defaultValue;
    }

    public String getMySQLPath()
    {
        return _mysqlPath;
    }

    public String getMySQLUser()
    {
        return _mysqlUser;
    }

    public String getMySQLPass()
    {
        return _mysqlPass;
    }

    public String getMySQLHost()
    {
        return _mysqlHost;
    }

    public abstract String getMySQLDatabaseName();
    public abstract String getMySQLSchemaPath();
}