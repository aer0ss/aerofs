package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.PolarisTestServer;
import com.aerofs.polaris.api.PolarisUtilities;
import com.aerofs.polaris.api.batch.transform.TransformBatch;
import com.aerofs.polaris.api.batch.transform.TransformBatchOperation;
import com.aerofs.polaris.api.operation.InsertChild;
import com.google.common.collect.ImmutableList;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static com.aerofs.polaris.api.types.ObjectType.FILE;
import static com.google.common.net.HttpHeaders.ACCEPT;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.jayway.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.equalTo;

public final class TestRoutes {

    static {
        RestAssured.config = PolarisHelpers.newRestAssuredConfig();
    }

    private static final byte[] HASH_BYTES = PolarisUtilities.hexDecode("95A8CD21628626307EEDD4439F0E40E3E5293AFD16305D8A4E82D9F851AE7AAF");
    private static final UserID USERID = UserID.fromInternal("test@aerofs.com");
    private static final DID DEVICE = DID.generate();
    private static final RequestSpecification AUTHENTICATED = PolarisHelpers.newAuthedAeroUserReqSpec(USERID, DEVICE);

    private static final MySQLDatabase database = new MySQLDatabase("test");
    private static final PolarisTestServer polaris = new PolarisTestServer();

    @ClassRule
    public static RuleChain rule = RuleChain.outerRule(database).around(polaris);

    @After
    public void afterTest() throws Exception {
        database.clear();
    }

    @Test
    public void shouldReachRoute3() { // POST /objects/{oid}
        SID store = SID.generate();

        // create a file in the store
        OID file = OID.generate();
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new InsertChild(file, FILE, "file", null))
                .and()
                .when().post(PolarisTestServer.getServiceURL() + "/objects/" + store.toStringFormal())
                .then().assertThat().statusCode(SC_OK);
    }

    @Test
    public void shouldReachRoute4() { // GET /transforms/{oid}
        SID store = SID.generate();
        PolarisHelpers.newFile(AUTHENTICATED, store, "file"); // ignore created file oid

        // try to get a list of transforms for the store
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(ACCEPT, APPLICATION_JSON)
                .and()
                .parameters("since", -1, "count", 10)
                .and()
                .when().get(PolarisTestServer.getServiceURL() + "/transforms/" + store.toStringFormal())
                .then()
                .assertThat().statusCode(equalTo(SC_OK));
    }

    @Test
    public void shouldReachRoute5() { // POST /batch/transforms
        SID store = SID.generate();
        OID file = PolarisHelpers.newFile(AUTHENTICATED, store, "file");

        // post at least one transform
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(ACCEPT, APPLICATION_JSON)
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new TransformBatch(ImmutableList.of(new TransformBatchOperation(store, new InsertChild(file, FILE, "file", null)))))
                .and()
                .when().post(PolarisTestServer.getServiceURL() + "/batch/transforms/")
                .then()
                .assertThat().statusCode(equalTo(SC_OK));
    }
}
