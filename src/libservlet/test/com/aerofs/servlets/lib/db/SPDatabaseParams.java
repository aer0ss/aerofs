/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

/**
 * SP database parameters for unit testing.
 */
public class SPDatabaseParams extends DatabaseParameters
{
    private static final String JUNIT_MYSQL_SP_SCHEMA_PATH_PARAMETER = "junit.mysqlSpSchemaPath";
    private static final String[] DEFAULT_SP_SCHEMA_PATHS = new String[] {
            "../src/spdb/src/main/resources/db/migration",
            "../../src/spdb/src/main/resources/db/migration"
    };
    private final String _mysqlSchemaPath;

    public SPDatabaseParams()
    {
        _mysqlSchemaPath = getOrDefault(JUNIT_MYSQL_SP_SCHEMA_PATH_PARAMETER,
                findExistingPath(DEFAULT_SP_SCHEMA_PATHS));
    }

    @Override
    public String getMySQLSchemaPath()
    {
        return _mysqlSchemaPath;
    }

    @Override
    public String getMySQLDatabaseName()
    {
        return "aerofs_sp_test";
    }

    @Override
    public String getMySQLSchemaName()
    {
        return "sp.sql";
    }
}
