package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.PolarisTestServer;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.batch.transform.TransformBatch;
import com.aerofs.polaris.api.batch.transform.TransformBatchOperation;
import com.aerofs.polaris.api.batch.transform.TransformBatchOperationResult;
import com.aerofs.polaris.api.batch.transform.TransformBatchResult;
import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.types.ObjectType;
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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SuppressWarnings("ConstantConditions")
public final class TestTransformBatchResource {

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
        // construct a number of files in a store
        SID store = SID.generate();

        TransformBatch batch = new TransformBatch(ImmutableList.of(
                new TransformBatchOperation(store, new InsertChild(OID.generate(), ObjectType.FILE, "file_1")),
                new TransformBatchOperation(store, new InsertChild(OID.generate(), ObjectType.FILE, "file_2")),
                new TransformBatchOperation(store, new InsertChild(OID.generate(), ObjectType.FILE, "file_3"))
        ));

        // attempt to reinsert filename into store to create:
        // store -> (folder_1 -> filename, filename)
        TransformBatchResult result = given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(batch)
                .and()
                .when().post(PolarisTestServer.getTransformBatchURL())
                .then()
                .extract().response().as(TransformBatchResult.class);

        assertThat(result.results, hasSize(3));

        for (TransformBatchOperationResult operationResult : result.results) {
            assertThat(operationResult.successful, is(true));
            assertThat(operationResult.updated, hasSize(1));
            assertThat(operationResult.updated.get(0).object.oid, equalTo(store));
        }

        // should have received a *single* notification, since all changes were to the same shared folder
        verify(polaris.getNotifier(), times(1)).notifyStoreUpdated(eq(store), any(Long.class));
    }

    @Test
    public void shouldReturnResultsForCompletedOperationsEvenIfSomeFailed() throws InterruptedException {
        // construct a number of files in store
        SID store = SID.generate();

        TransformBatch batch = new TransformBatch(ImmutableList.of(
                new TransformBatchOperation(store, new InsertChild(OID.generate(), ObjectType.FILE, "file_1")),
                new TransformBatchOperation(store, new InsertChild(OID.generate(), ObjectType.FILE, "file_2")),
                new TransformBatchOperation(store, new InsertChild(OID.generate(), ObjectType.FILE, "file_1"))
        ));

        // attempt to reinsert filename into store to create:
        // store -> (folder_1 -> filename, filename)
        TransformBatchResult result = given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(batch)
                .and()
                .when().post(PolarisTestServer.getTransformBatchURL())
                .then()
                .extract().response().as(TransformBatchResult.class);

        assertThat(result.results, hasSize(3));

        TransformBatchOperationResult operationResult;

        // first result
        operationResult = result.results.get(0);
        assertThat(operationResult.successful, is(true));
        assertThat(operationResult.updated, hasSize(1));
        assertThat(operationResult.updated.get(0).object.oid, equalTo(store));

        // second result
        operationResult = result.results.get(1);
        assertThat(operationResult.successful, is(true));
        assertThat(operationResult.updated, hasSize(1));
        assertThat(operationResult.updated.get(0).object.oid, equalTo(store));

        // third result
        operationResult = result.results.get(2);
        assertThat(operationResult.successful, is(false));
        assertThat(operationResult.error.errorCode, equalTo(PolarisError.NAME_CONFLICT));

        // should have received a notification for the completed operation
        verify(polaris.getNotifier(), times(1)).notifyStoreUpdated(eq(store), any(Long.class));
    }

    @Test
    public void shouldAbortBatchEarlyAndReturnResultsForCompletedOperations() throws InterruptedException {
        // construct a number of files in a store
        SID store = SID.generate();

        TransformBatch batch = new TransformBatch(ImmutableList.of(
                new TransformBatchOperation(store, new InsertChild(OID.generate(), ObjectType.FILE, "file_1")),
                new TransformBatchOperation(store, new InsertChild(OID.generate(), ObjectType.FILE, "file_1")),
                new TransformBatchOperation(store, new InsertChild(OID.generate(), ObjectType.FILE, "file_2"))
        ));

        // attempt to reinsert filename into store to create:
        // store -> (folder_1 -> filename, filename)
        TransformBatchResult result = given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(batch)
                .and()
                .when().post(PolarisTestServer.getTransformBatchURL())
                .then()
                .extract().response().as(TransformBatchResult.class);

        assertThat(result.results, hasSize(2));

        TransformBatchOperationResult operationResult;

        // first result
        operationResult = result.results.get(0);
        assertThat(operationResult.successful, is(true));
        assertThat(operationResult.updated, hasSize(1));
        assertThat(operationResult.updated.get(0).object.oid, equalTo(store));

        // second result
        operationResult = result.results.get(1);
        assertThat(operationResult.successful, is(false));
        assertThat(operationResult.error.errorCode, equalTo(PolarisError.NAME_CONFLICT));

        // should have received a notification for the completed operation
        verify(polaris.getNotifier(), times(1)).notifyStoreUpdated(eq(store), any(Long.class));
    }

    @Test
    public void shouldAbortBatchEarlyIfAccessChecksFailAndReturnResultsForCompletedOperations() throws InterruptedException {
        // construct a number of files in a store
        SID store = SID.generate();

        TransformBatch batch = new TransformBatch(ImmutableList.of(
                new TransformBatchOperation(store, new InsertChild(OID.generate(), ObjectType.FILE, "file_1")),
                new TransformBatchOperation(store, new InsertChild(OID.generate(), ObjectType.FILE, "file_1")),
                new TransformBatchOperation(store, new InsertChild(OID.generate(), ObjectType.FILE, "file_2"))
        ));

        // attempt to reinsert filename into store to create:
        // store -> (folder_1 -> filename, filename)
        TransformBatchResult result = given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(batch)
                .and()
                .when().post(PolarisTestServer.getTransformBatchURL())
                .then()
                .extract().response().as(TransformBatchResult.class);

        assertThat(result.results, hasSize(2));

        TransformBatchOperationResult operationResult;

        // first result
        operationResult = result.results.get(0);
        assertThat(operationResult.successful, is(true));
        assertThat(operationResult.updated, hasSize(1));
        assertThat(operationResult.updated.get(0).object.oid, equalTo(store));

        // second result
        operationResult = result.results.get(1);
        assertThat(operationResult.successful, is(false));
        assertThat(operationResult.error.errorCode, equalTo(PolarisError.NAME_CONFLICT));

        // should have received a notification for the completed operation
        verify(polaris.getNotifier(), times(1)).notifyStoreUpdated(eq(store), any(Long.class));
    }
}