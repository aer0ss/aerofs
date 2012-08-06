/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sp;

import com.aerofs.lib.Util;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

public class LocalTestDatabase
{
    private static final Logger l = Util.l(LocalTestDatabase.class);

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
    private String _mysqlUser;
    private String _mysqlPass;
    private String _mysqlHost;
    private String _mysqlSchema;

    private String _mysqlSchemaPath;

    public LocalTestDatabase(String schema, String schemaPath)
    {
        this._mysqlSchema = schema;
        this._mysqlSchemaPath = schemaPath;

        // These are just pulled from the env, but keep them here so that we eliminate duped code
        // in the derived classes.
        _mysqlUser = getOrDefault(JUNIT_MYSQL_USER_PARAMETER, DEFAULT_LOCAL_MYSQL_USER);
        _mysqlPass = getOrDefault(JUNIT_MYSQL_PASS_PARAMETER, DEFAULT_LOCAL_MYSQL_PASS);
        _mysqlHost = getOrDefault(JUNIT_MYSQL_HOST_PARAMETER, DEFAULT_LOCAL_MYSQL_HOST);
    }

    public void init_()
            throws SQLException, ClassNotFoundException, InterruptedException, IOException
    {

        String mysqlPath = getOrDefault(JUNIT_MYSQL_PATH_PARAMETER, DEFAULT_LOCAL_MYSQL_PATH);

        String dropProcedures = String.format(
                 "%s/mysql -u%s -h%s -p%s -e \"delete from mysql.proc where db='%s' and type='PROCEDURE'\"",
                mysqlPath, _mysqlUser, _mysqlHost, _mysqlPass, _mysqlSchema);

        String dropDatabase = String.format(
                "%s/mysql -u%s -h%s -p%s -e 'drop schema if exists %s'",
                mysqlPath, _mysqlUser, _mysqlHost, _mysqlPass, _mysqlSchema);

        String createDatabase = String.format(
                "%s/mysql -u%s -h%s -p%s -e 'create database if not exists %s'",
                mysqlPath, _mysqlUser, _mysqlHost, _mysqlPass, _mysqlSchema);

        String loadSchema = String.format(
                "%s/mysql -u%s -h%s -p%s %s < %s",
                mysqlPath, _mysqlUser, _mysqlHost, _mysqlPass, _mysqlSchema, _mysqlSchemaPath);


        l.info("setting up database schema");
        Runtime runtime = Runtime.getRuntime();

        {
            Process p1 = runtime.exec(new String[]{"/bin/bash", "-c", dropProcedures});
            p1.waitFor();
            l.info("drop pr exitcode:" + p1.exitValue());
            if (p1.exitValue() != 0) {
                throw new RuntimeException("failed to drop pr (cmd: " + dropProcedures + ")");
            }
        }

        {
            Process p2 = runtime.exec(new String[]{"/bin/bash", "-c", dropDatabase});
            p2.waitFor();
            l.info("drop db exitcode:" + p2.exitValue());
            if (p2.exitValue() != 0) {
                throw new RuntimeException("failed to drop db (cmd: " + dropDatabase + ")");
            }
        }

        {
            Process p3 = runtime.exec(new String[]{"/bin/bash", "-c", createDatabase});
            p3.waitFor();
            l.info("make db exitcode:" + p3.exitValue());
            if (p3.exitValue() != 0) {
                throw new RuntimeException("failed to make db (cmd: " + createDatabase  + ")");
            }
        }

        {
            Process p4 = runtime.exec(new String[]{"/bin/bash", "-c", loadSchema});
            p4.waitFor();
            l.info("lod ddl exitcode:" + p4.exitValue());
            if (p4.exitValue() != 0) {
                throw new RuntimeException("failed to lod ddl (cmd: " + loadSchema + ")");
            }
        }
    }

    public static String getOrDefault(String parameter, String defaultValue)
    {
        return System.getProperties().containsKey(parameter)
                ?
                System.getProperty(parameter)
                :
                defaultValue;
    }

    public String getUser()
    {
        return _mysqlUser;
    }

    public String getPass()
    {
        return _mysqlPass;
    }

    public String getHost()
    {
        return _mysqlHost;
    }

    public String getSchema()
    {
        return _mysqlSchema;
    }
}
