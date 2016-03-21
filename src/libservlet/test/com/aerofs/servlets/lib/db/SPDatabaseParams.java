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

    public static final String[] TABLES = {
            "acl", "cert", "device", "sharing_group_members", "sharing_group_shares",
            "sharing_groups", "url_sharing", "shared_folder_names",
            "shared_folder", "two_factor_secret", "two_factor_recovery",
            "user", "organization_invite", "organization", "signup_code", "settings_token",
            "sa_tokens"
    };
}
