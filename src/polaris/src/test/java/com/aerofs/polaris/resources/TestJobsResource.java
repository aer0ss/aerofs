package com.aerofs.polaris.resources;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.db.DatabaseConfiguration;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.skife.jdbi.v2.DBI;

import javax.ws.rs.core.Response;

import static com.jayway.restassured.RestAssured.given;
import static org.junit.Assert.assertThat;

public class TestJobsResource {
    static {
        RestAssured.config = PolarisHelpers.newRestAssuredConfig();
    }

    private static final UserID USERID = UserID.fromInternal("test@aerofs.com");
    private static final DID DEVICE = DID.generate();
    private final RequestSpecification verified = PolarisHelpers.newAuthedAeroUserReqSpec(USERID, DEVICE);
    private PolarisTestServer polaris = new PolarisTestServer();
    private DBI dbi;

    @Rule
    public RuleChain chain = RuleChain.outerRule(new MySQLDatabase("test")).around(polaris);

    @Before
    public void setup() throws Exception {
        PolarisConfiguration configuration = Configuration.loadYAMLConfigurationFromResources(Polaris.class, "polaris_test_server.yml");
        DatabaseConfiguration database = configuration.getDatabase();
        BasicDataSource dataSource = (BasicDataSource) Databases.newDataSource(database);

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
    }

    @Test
    public void migrationShouldReturnAssociatedJobID() {
        SID store = SID.rootSID(USERID);
        OID folder = PolarisHelpers.newFolder(verified, store, "folder_1");

        OperationResult result = PolarisHelpers.shareFolder(verified, folder);

        assertThat(result.jobID, Matchers.notNullValue());
    }

    @Test
    public void migrationJobsShouldReportCompletion() throws Exception {
        SID store = SID.rootSID(USERID);
        OID folder = PolarisHelpers.newFolder(verified, store, "folder_1");
        UniqueID jobID = PolarisHelpers.shareFolder(verified, folder).jobID;

        JobStatus status = PolarisHelpers.waitForJobCompletion(verified, jobID, 5);

        assertThat("job failed to complete successfully", status, Matchers.equalTo(JobStatus.COMPLETED));
    }

    @Test
    public void shouldReportJobsAsRunning() {
        SID store = SID.generate();
        UniqueID jobID = UniqueID.generate();
        dbi.inTransaction((conn, status) -> {
            Migrations migrations = conn.attach(Migrations.class);
            migrations.addStoreMigration(SID.convertedStoreSID2folderOID(store), store, jobID, DEVICE, JobStatus.RUNNING);
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
