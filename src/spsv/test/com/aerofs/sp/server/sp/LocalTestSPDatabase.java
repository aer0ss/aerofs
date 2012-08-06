package com.aerofs.sp.server.sp;

import java.io.IOException;
import java.sql.SQLException;

public class LocalTestSPDatabase extends SPDatabase
{
    private static final String JUNIT_MYSQL_SP_SCHEMA_PATH_PARAMETER = "junit.mysqlSpSchemaPath";
    private static final String DEFAULT_SP_SCHEMA_PATH = "../src/spsv/resources/schemas/sp.sql";

    public void init_()
            throws SQLException, ClassNotFoundException, InterruptedException, IOException
    {
        LocalTestDatabase testdb = new LocalTestDatabase("aerofs_sp_staging",
                LocalTestDatabase.getOrDefault(
                        JUNIT_MYSQL_SP_SCHEMA_PATH_PARAMETER,
                        DEFAULT_SP_SCHEMA_PATH));

        testdb.init_();
        super.init_(testdb.getHost(), testdb.getSchema(), testdb.getUser(), testdb.getPass());
    }
}