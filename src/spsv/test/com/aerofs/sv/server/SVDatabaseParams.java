/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sv.server;

import com.aerofs.servlets.lib.db.DatabaseParameters;

/**
 * SP database parameters for unit testing.
 */
public class SVDatabaseParams extends DatabaseParameters
{
    private static final String JUNIT_MYSQL_SV_SCHEMA_PATH_PARAMETER = "junit.mysqlSvSchemaPath";
    private static final String[] DEFAULT_SV_SCHEMA_PATHS = new String[] {
            "../src/spsv/resources/schemas",
            "../../src/spsv/resources/schemas"
    };
    private final String _mysqlSchemaPath;

    public SVDatabaseParams()
    {
        _mysqlSchemaPath = getOrDefault(JUNIT_MYSQL_SV_SCHEMA_PATH_PARAMETER,
                findExistingPath(DEFAULT_SV_SCHEMA_PATHS));
    }

    @Override
    public String getMySQLSchemaPath()
    {
        return _mysqlSchemaPath;
    }

    @Override
    public String getMySQLDatabaseName()
    {
        return "aerofs_sv_beta";
    }

    @Override
    public String getMySQLSchemaName()
    {
        return "sv.sql";
    }
}
