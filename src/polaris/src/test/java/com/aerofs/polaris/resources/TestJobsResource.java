package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.Databases;
import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.*;
import com.aerofs.polaris.*;
import com.aerofs.polaris.api.operation.OperationResult;
import com.aerofs.polaris.api.types.JobStatus;
import com.aerofs.polaris.dao.Migrations;
import com.aerofs.polaris.dao.types.*;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.flywaydb.core.Flyway;
import org.hamcrest.Matchers;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.skife.jdbi.v2.DBI;

import javax.ws.rs.core.Response;

import java.sql.SQLException;

import static com.aerofs.polaris.PolarisTestServer.CONFIGURATION;
import static com.jayway.restassured.RestAssured.given;
import static org.junit.Assert.assertThat;

public class TestJobsResource {
    static {
        RestAssured.config = PolarisHelpers.newRestAssuredConfig();
    }

    private static final UserID USERID = UserID.fromInternal("test@aerofs.com");
    private static final DID DEVICE = DID.generate();
    private static final RequestSpecification verified = PolarisHelpers.newAuthedAeroUserReqSpec(USERID, DEVICE);
    private static PolarisTestServer polaris = new PolarisTestServer();
    private static MySQLDatabase database = new MySQLDatabase("test");
    private static DBI dbi;
    private static BasicDataSource dataSource;

    @ClassRule
    public static RuleChain chain = RuleChain.outerRule(database).around(polaris);

    @BeforeClass
    public static void startup() throws Exception {
        dataSource = (BasicDataSource) Databases.newDataSource(CONFIGURATION.getDatabase());

        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.migrate();

        // setup JDBI
        dbi = Databases.newDBI(dataSource);
        dbi.registerArgumentFactory(new UniqueIDTypeArgument.UniqueIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new OIDTypeArgument.OIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new SIDTypeArgument.SIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new DIDTypeArgument.DIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new ObjectTypeArgument.ObjectTypeArgumentFactory());
        dbi.registerArgumentFactory(new TransformTypeArgument.TransformTypeArgumentFactory());
        dbi.registerArgumentFactory(new JobStatusArgument.JobStatusArgumentFactory());
        dbi.registerArgumentFactory(new LockStatusArgument.LockStatusArgumentFactory());
    }

    @After
    public void cleanup() throws Exception
    {
        database.clear();
    }

    @AfterClass
    public static void shutdown() throws Exception
    {
        try {
            dataSource.close();
        } catch (SQLException e) {
            // noop
        }
    }

    @Test
    public void migrationShouldReturnAssociatedJobID() {
        SID store = SID.rootSID(USERID);
        OID folder = PolarisHelpers.newFolder(verified, store, "folder_1");

        OperationResult result = PolarisHelpers.shareFolder(verified, store, folder);

        assertThat(result.jobID, Matchers.notNullValue());
    }

    @Test
    public void migrationJobsShouldReportCompletion() throws Exception {
        SID store = SID.rootSID(USERID);
        OID folder = PolarisHelpers.newFolder(verified, store, "folder_1");
        UniqueID jobID = PolarisHelpers.shareFolder(verified, store, folder).jobID;

        JobStatus status = PolarisHelpers.waitForJobCompletion(verified, jobID, 5);

        assertThat("job failed to complete successfully", status, Matchers.equalTo(JobStatus.COMPLETED));
    }

    @Test
    public void shouldReportJobsAsRunning() {
        SID store = SID.generate();
        UniqueID jobID = UniqueID.generate();
        dbi.inTransaction((conn, status) -> {
            Migrations migrations = conn.attach(Migrations.class);
            migrations.addMigration(SID.convertedStoreSID2folderOID(store), store, jobID, DEVICE, JobStatus.RUNNING);
            return null;
        });

        assertThat(PolarisHelpers.getJobStatus(verified, jobID), Matchers.equalTo(JobStatus.RUNNING));
    }

    @Test
    public void shouldFailToReportStatusOfNonexistentJob() {
        SID nonexistent = SID.generate();

        given()
                .spec(verified)
                .and()
                .when().get(PolarisTestServer.getJobURL(nonexistent))
                .then()
                .assertThat().statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }

}
