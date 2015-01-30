package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.core.Identifiers;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.PolarisTestServer;
import com.aerofs.polaris.api.batch.location.LocationBatch;
import com.aerofs.polaris.api.batch.location.LocationBatchOperation;
import com.aerofs.polaris.api.batch.location.LocationUpdateType;
import com.aerofs.polaris.api.batch.transform.TransformBatch;
import com.aerofs.polaris.api.batch.transform.TransformBatchOperation;
import com.aerofs.polaris.api.operation.InsertChild;
import com.google.common.collect.ImmutableList;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static com.aerofs.polaris.api.types.ObjectType.FILE;
import static com.google.common.net.HttpHeaders.ACCEPT;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.jayway.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.equalTo;

public final class TestRoutes {

    static {
        RestAssured.config = PolarisHelpers.newRestAssuredConfig();
    }

    private static final String USERID = "test@aerofs.com";
    private static final String DEVICE = Identifiers.newRandomDevice();
    private static final RequestSpecification AUTHENTICATED = PolarisHelpers.newAuthedAeroUserReqSpec(USERID, DEVICE);

    private final MySQLDatabase database = new MySQLDatabase("test");
    private final PolarisTestServer polaris = new PolarisTestServer();

    @Rule
    public RuleChain chain = RuleChain.outerRule(database).around(polaris);

    @Test
    public void shouldReachRoute0() { // POST /objects/{oid}/versions/{version}/locations/{did}
        // create a root folder and a file under it
        String root = Identifiers.newRandomSharedFolder();
        String file = PolarisHelpers.newFile(AUTHENTICATED, root, "file");

        // this route should exist
        given()
                .spec(AUTHENTICATED)
                .and()
                .when().post(PolarisTestServer.getServiceURL() + "/objects/" + file + "/versions/" + 0 + "/locations/" + DEVICE)
                .then()
                .assertThat().statusCode(equalTo(SC_NO_CONTENT));
    }

    @Test
    public void shouldReachRoute1() { // GET /objects/{oid}/versions/{version}/locations/
        // create a root folder and a file under it (along with some content)
        String root = Identifiers.newRandomSharedFolder();
        String file = PolarisHelpers.newFile(AUTHENTICATED, root, "file");
        PolarisHelpers.newFileContent(AUTHENTICATED, file, 0, "SOME_HASH", 1, System.currentTimeMillis());

        // add a location at which that file is present
        given()
                .spec(AUTHENTICATED)
                .and()
                .when().post(PolarisTestServer.getServiceURL() + "/objects/" + file + "/versions/" + 0 + "/locations/"+ DEVICE)
                .then()
                .assertThat().statusCode(equalTo(SC_NO_CONTENT));

        // attempt to get info about the locations at which that file is present
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(ACCEPT, APPLICATION_JSON)
                .when().get(PolarisTestServer.getServiceURL() + "/objects/" + file + "/versions/" + 0 + "/locations/")
                .then()
                .assertThat().statusCode(equalTo(SC_OK));
    }

    @Test
    public void shouldReachRoute2() { // DELETE /objects/{oid}/versions/{version}/locations/{did}
        // create a root folder and a file under it (along with some content)
        String root = Identifiers.newRandomSharedFolder();
        String file = PolarisHelpers.newFile(AUTHENTICATED, root, "file");
        PolarisHelpers.newFileContent(AUTHENTICATED, file, 0, "SOME_HASH", 1, System.currentTimeMillis());

        // add a location at which that file is present
        given()
                .spec(AUTHENTICATED)
                .and()
                .when().post(PolarisTestServer.getServiceURL() + "/objects/" + file + "/versions/" + 0 + "/locations/" + DEVICE)
                .then()
                .assertThat().statusCode(equalTo(SC_NO_CONTENT));

        // now, delete that location
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(ACCEPT, APPLICATION_JSON)
                .when().delete(PolarisTestServer.getServiceURL() + "/objects/" + file + "/versions/" + 0 + "/locations/" + DEVICE)
                .then()
                .assertThat().statusCode(equalTo(SC_NO_CONTENT));

    }

    @Test
    public void shouldReachRoute3() { // POST /objects/{oid}
        // create a root folder
        String root = Identifiers.newRandomSharedFolder();

        // create a file in that root folder
        String file = Identifiers.newRandomObject();
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new InsertChild(file, FILE, "file"))
                .and()
                .when().post(PolarisTestServer.getServiceURL() + "/objects/" + root)
                .then().assertThat().statusCode(SC_OK);
    }

    @Test
    public void shouldReachRoute4() { // GET /transforms/{oid}
        // create a root folder and a file under it (along with some content)
        String root = Identifiers.newRandomSharedFolder();
        String file = PolarisHelpers.newFile(AUTHENTICATED, root, "file");

        // try to get a list of transforms for the root
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(ACCEPT, APPLICATION_JSON)
                .and()
                .parameters("since", -1, "count", 10)
                .and()
                .when().get(PolarisTestServer.getServiceURL() + "/transforms/" + root)
                .then()
                .assertThat().statusCode(equalTo(SC_OK));
    }

    @Test
    public void shouldReachRoute5() { // POST /batch/transforms
        // create a root folder and a file under it (along with some content)
        String root = Identifiers.newRandomSharedFolder();
        String file = PolarisHelpers.newFile(AUTHENTICATED, root, "file");

        // post at least one transform
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(ACCEPT, APPLICATION_JSON)
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new TransformBatch(ImmutableList.of(new TransformBatchOperation(root, new InsertChild(file, FILE, "file")))))
                .and()
                .when().post(PolarisTestServer.getServiceURL() + "/batch/transforms/")
                .then()
                .assertThat().statusCode(equalTo(SC_OK));
    }

    @Test
    public void shouldReachRoute6() { // POST /batch/locations
        // create a root folder and a file under it (along with some content)
        String root = Identifiers.newRandomSharedFolder();
        String file = PolarisHelpers.newFile(AUTHENTICATED, root, "file");

        // post at least one location update
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(ACCEPT, APPLICATION_JSON)
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new LocationBatch(ImmutableList.of(new LocationBatchOperation(file, 0, DEVICE, LocationUpdateType.INSERT))))
                .and()
                .when().post(PolarisTestServer.getServiceURL() + "/batch/locations/")
                .then()
                .assertThat().statusCode(equalTo(SC_OK));
    }
}
