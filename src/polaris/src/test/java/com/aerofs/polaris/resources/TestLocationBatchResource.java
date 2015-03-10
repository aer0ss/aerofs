package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.PolarisTestServer;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.batch.location.LocationBatch;
import com.aerofs.polaris.api.batch.location.LocationBatchOperation;
import com.aerofs.polaris.api.batch.location.LocationBatchOperationResult;
import com.aerofs.polaris.api.batch.location.LocationBatchResult;
import com.aerofs.polaris.api.batch.location.LocationUpdateType;
import com.google.common.collect.ImmutableList;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.jayway.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;

public final class TestLocationBatchResource {

    static {
        RestAssured.config = PolarisHelpers.newRestAssuredConfig();
    }

    private static final UserID USERID = UserID.fromInternal("test@aerofs.com");
    private static final DID DEVICE = DID.generate();
    private static final RequestSpecification AUTHENTICATED = PolarisHelpers.newAuthedAeroUserReqSpec(USERID, DEVICE);

    private final MySQLDatabase database = new MySQLDatabase("test");
    private final PolarisTestServer polaris = new PolarisTestServer();

    @Rule
    public RuleChain chain = RuleChain.outerRule(database).around(polaris);

    @Test
    public void shouldSuccessfullyCompleteAllOperationsInBatch() throws InterruptedException {
        SID store = SID.generate();
        OID object0 = PolarisHelpers.newFile(AUTHENTICATED, store, "file0");
        OID object1 = PolarisHelpers.newFile(AUTHENTICATED, store, "file1");
        OID object2 = PolarisHelpers.newFile(AUTHENTICATED, store, "file2");

        LocationBatch batch = new LocationBatch(ImmutableList.of(
                new LocationBatchOperation(object0, 0, DEVICE, LocationUpdateType.INSERT),
                new LocationBatchOperation(object1, 0, DEVICE, LocationUpdateType.INSERT),
                new LocationBatchOperation(object2, 0, DEVICE, LocationUpdateType.INSERT)
        ));

        // attempt to add a number of locations
        LocationBatchResult result = given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(batch)
                .when().post(PolarisTestServer.getLocationBatchURL())
                .then()
                .extract().response().as(LocationBatchResult.class);

        assertThat(result.results, hasSize(3));

        for (LocationBatchOperationResult operationResult : result.results) {
            assertThat(operationResult.successful, is(true));
        }
    }

    @Test
    public void shouldReturnResultsForCompletedOperationsEvenIfSomeFailed() throws InterruptedException {
        // construct a number of files in a store
        SID store = SID.generate();
        OID object0 = PolarisHelpers.newFile(AUTHENTICATED, store, "file0");
        OID object1 = PolarisHelpers.newFile(AUTHENTICATED, store, "file1");

        LocationBatch batch = new LocationBatch(ImmutableList.of(
                new LocationBatchOperation(object0, 0, DEVICE, LocationUpdateType.INSERT),
                new LocationBatchOperation(object1, 0, DEVICE, LocationUpdateType.INSERT),
                new LocationBatchOperation(OID.generate(), 0, DEVICE, LocationUpdateType.INSERT) // try to add a location for a random object
        ));

        // attempt to add a number of locations
        LocationBatchResult result = given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(batch)
                .when().post(PolarisTestServer.getLocationBatchURL())
                .then()
                .extract().response().as(LocationBatchResult.class);

        assertThat(result.results, hasSize(3));

        LocationBatchOperationResult operationResult;

        // first result
        operationResult = result.results.get(0);
        assertThat(operationResult.successful, is(true));

        // second result
        operationResult = result.results.get(1);
        assertThat(operationResult.successful, is(true));

        // third result
        operationResult = result.results.get(2);
        assertThat(operationResult.successful, is(false));
        assertThat(operationResult.error.errorCode, equalTo(PolarisError.NO_SUCH_OBJECT));
    }

    @Test
    public void shouldAbortBatchEarlyAndReturnResultsForCompletedOperations() throws InterruptedException {
        // construct a number of files in a store
        SID store = SID.generate();
        OID object0 = PolarisHelpers.newFile(AUTHENTICATED, store, "file0");
        OID object1 = PolarisHelpers.newFile(AUTHENTICATED, store, "file1");

        LocationBatch batch = new LocationBatch(ImmutableList.of(
                new LocationBatchOperation(object0, 0, DEVICE, LocationUpdateType.INSERT),
                new LocationBatchOperation(OID.generate(), 0, DEVICE, LocationUpdateType.INSERT), // try to add a location for a random object
                new LocationBatchOperation(object1, 0, DEVICE, LocationUpdateType.INSERT)
        ));

        // attempt to add a number of locations
        LocationBatchResult result = given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(batch)
                .when().post(PolarisTestServer.getLocationBatchURL())
                .then()
                .extract().response().as(LocationBatchResult.class);

        assertThat(result.results, hasSize(2)); // the batch aborts early

        LocationBatchOperationResult operationResult;

        // first result
        operationResult = result.results.get(0);
        assertThat(operationResult.successful, is(true));

        // second result
        operationResult = result.results.get(1);
        assertThat(operationResult.successful, is(false));
        assertThat(operationResult.error.errorCode, equalTo(PolarisError.NO_SUCH_OBJECT));
    }

    @Test
    public void shouldAbortBatchEarlyReturnResultsForCompletedOperationsEvenIfSomeFailedDueToAccessRestrictions() throws AccessException {
        // two shared folders
        SID store0 = SID.generate();
        SID store1 = SID.generate();

        // create a folder hierarchy for store0
        OID folder00 = PolarisHelpers.newFolder(AUTHENTICATED, store0, "folder00");
        OID folder000 = PolarisHelpers.newFolder(AUTHENTICATED, folder00, "folder000");

        // create a folder hierarchy for store1
        OID folder10 = PolarisHelpers.newFolder(AUTHENTICATED, store1, "folder10");
        OID folder100 = PolarisHelpers.newFolder(AUTHENTICATED, folder10, "folder100");

        // now, create files at the deepest level
        OID object0 = PolarisHelpers.newFile(AUTHENTICATED, folder000, "file0");
        OID object1 = PolarisHelpers.newFile(AUTHENTICATED, folder100, "file1");
        OID object2 = PolarisHelpers.newFile(AUTHENTICATED, folder100, "file2");

        // set the access manager to *reject* changes to store0
        doThrow(new AccessException(USERID, store0, Access.WRITE)).when(polaris.getAccessManager()).checkAccess(eq(USERID), eq(store0), anyVararg());

        // now, submit a batch
        LocationBatch batch = new LocationBatch(ImmutableList.of(
                new LocationBatchOperation(object1, 0, DEVICE, LocationUpdateType.INSERT), // this should succeed
                new LocationBatchOperation(object2, 0, DEVICE, LocationUpdateType.INSERT), // this should succeed
                new LocationBatchOperation(object0, 0, DEVICE, LocationUpdateType.INSERT)  // this should fail
        ));

        // attempt to add a number of locations
        LocationBatchResult result = given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(batch)
                .when().post(PolarisTestServer.getLocationBatchURL())
                .then()
                .extract().response().as(LocationBatchResult.class);

        assertThat(result.results, hasSize(3));

        LocationBatchOperationResult operationResult;

        // first result
        operationResult = result.results.get(0);
        assertThat(operationResult.successful, is(true));

        // second result
        operationResult = result.results.get(1);
        assertThat(operationResult.successful, is(true));

        // third result
        operationResult = result.results.get(2);
        assertThat(operationResult.successful, is(false));
        assertThat(operationResult.error.errorCode, equalTo(PolarisError.INSUFFICIENT_PERMISSIONS));
    }
}