package db.migration;

import com.aerofs.lib.LibParam;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class V2_0_1__import_bifrost_db implements JdbcMigration
{
    public void migrate(Connection connection)
            throws Exception
    {
        DataSource ds = bifrostDataSource();

        // First, make sure that the bifrost schema is completely up-to-date before trying to import it.
        try {
            Flyway bifrostFlyway = new Flyway();
            bifrostFlyway.setDataSource(ds);
            bifrostFlyway.setBaselineOnMigrate(true);
            bifrostFlyway.setValidateOnMigrate(false);
            bifrostFlyway.setSchemas("bifrost");
            bifrostFlyway.setLocations("legacybifrostdb/migration");
            bifrostFlyway.migrate();
        } catch (FlywayException e) {
            System.err.println(e.getMessage());
            throw e;
        }

        // Migrate data from the bifrost DB, if the DB exists.
        Connection bifrostConn;
        bifrostConn = ds.getConnection();
        bifrostConn.setAutoCommit(false);
        migrateDbContent(bifrostConn, connection);
        bifrostConn.commit();
    }

    private static DataSource bifrostDataSource()
    {
        PoolProperties p = new PoolProperties();
        p.setUrl("jdbc:mysql://" + LibParam.MYSQL.MYSQL_ADDRESS + "/bifrost");
        p.setUsername(LibParam.MYSQL.MYSQL_USER);
        p.setPassword(LibParam.MYSQL.MYSQL_PASS);

        p.setDriverClassName(LibParam.MYSQL.MYSQL_DRIVER);
        p.setTestWhileIdle(false);
        p.setTestOnBorrow(true);
        p.setTestOnReturn(false);
        p.setValidationQuery("SELECT 1");
        p.setValidationQueryTimeout(30000);
        p.setMaxActive(8);
        p.setMinIdle(4);
        p.setLogAbandoned(true);
        p.setRemoveAbandoned(true);
        p.setRemoveAbandonedTimeout(30);
        p.setConnectionProperties("cachePrepStmts=true; autoReconnect=true; " +
                "useUnicode=true; characterEncoding=utf8;");

        return new DataSource(p);
    }

    // List of tables to attempt to migrate, in the order they should be migrated.
    private static String[] tablesToMigrate = new String[] {
            "resourceserver",
            "ResourceServer_scopes",
            "AbstractEntity",
            "client",
            "client_attributes",
            "Client_redirectUris",
            "Client_scopes",
            "authorizationrequest",
            "AuthorizationRequest_grantedScopes",
            "AuthorizationRequest_requestedScopes",
            "accesstoken",
            "AccessToken_scopes",
    };

    private static void migrateDbContent(Connection sourceConn, Connection destConn)
            throws Exception
    {
        Set<String> sourceTables = tableSet(sourceConn);
        Set<String> destTables = tableSet(destConn);
        for (String table : tablesToMigrate) {
            // Preflight checks: verify that all the tables in sourceConn exist in destConn
            if (!sourceTables.contains(table)) {
                throw new Exception("Schema mismatch - expected source DB to include table " + table);
            }
            if (!destTables.contains(table)) {
                throw new Exception("Schema mismatch - expected destination DB to include table " + table);
            }
            System.err.println("Migrating table " + table);
            migrateTable(sourceConn, destConn, table);
        }
    }

    private static Set<String> tableSet(Connection conn)
            throws SQLException
    {
        Set<String> tables = Sets.newHashSet();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, "", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private static void migrateTable(Connection sourceConn, Connection destConn, String table)
            throws Exception
    {
        // preflight checks: verify that the table has the same column names and types in source and dest DBs
        HashMap<String, String> srcColumns = columnInfo(sourceConn, table);
        HashMap<String, String> destColumns = columnInfo(destConn, table);
        Preconditions.checkState(srcColumns.equals(destColumns));

        // Looks good. Stream rows from one DB to the other.
        // SELECT each of the columns, then INSERT each row into the other DB
        List<String> columnNames = srcColumns.keySet().stream().collect(Collectors.toList());
        String commaSeparatedColumns = Joiner.on(",").join(
                columnNames.stream()
                        .map((col) -> "`" + col + "`")
                        .collect(Collectors.toList())
        );
        String commaSeparatedQmarks = Joiner.on(",").join(
                columnNames.stream()
                        .map((col) -> "?")
                        .collect(Collectors.toList())
        );
        String selectQuery = "SELECT " + commaSeparatedColumns + " FROM " + table;
        try (PreparedStatement psSelect = sourceConn.prepareStatement(selectQuery)) {
            try (ResultSet rs = psSelect.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    String insertQuery = "INSERT INTO " + table + "(" + commaSeparatedColumns + ")" +
                            " VALUES (" + commaSeparatedQmarks + ")";
                    try (PreparedStatement psInsert = destConn.prepareStatement(insertQuery)) {
                        // SQL parameter and ResultSet indices start at 1.
                        for (int i = 1; i <= md.getColumnCount(); i++) {
                            psInsert.setObject(i, rs.getObject(i), md.getColumnType(i));
                        }
                        psInsert.execute();
                    }
                }
            }
        }
    }

    /**
     * Returns a map of column name -> column type string
     */
    private static HashMap<String, String> columnInfo(Connection conn, String table)
            throws SQLException
    {
        HashMap<String, String> columns = Maps.newHashMap();
        DatabaseMetaData dmd = conn.getMetaData();
        try (ResultSet rs = dmd.getColumns(null, null, table, ""))
        {
            while (rs.next()) {
                Preconditions.checkArgument(rs.getString("TABLE_NAME").equals(table));
                columns.put(rs.getString("COLUMN_NAME"), rs.getString("TYPE_NAME"));
            }
        }
        return columns;
    }
}
