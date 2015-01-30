package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.core.Identifiers;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public final class TestTransformBatchResource {

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
    public void shouldSuccessfullyCompleteAllOperationsInBatch() throws InterruptedException {
        // construct a number of files in root
        String root = Identifiers.newRandomSharedFolder();

        TransformBatch batch = new TransformBatch(ImmutableList.of(
                new TransformBatchOperation(root, new InsertChild(Identifiers.newRandomObject(), ObjectType.FILE, "file_1")),
                new TransformBatchOperation(root, new InsertChild(Identifiers.newRandomObject(), ObjectType.FILE, "file_2")),
                new TransformBatchOperation(root, new InsertChild(Identifiers.newRandomObject(), ObjectType.FILE, "file_3"))
        ));

        // attempt to reinsert filename into root to create:
        // root -> (folder_1 -> filename, filename)
        TransformBatchResult result = given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(batch)
                .and()
                .when().post(PolarisTestServer.getTransformBatchURL())
                .then()
                .extract().response().as(TransformBatchResult.class);

        assertThat(result.getResults(), hasSize(3));

        for (TransformBatchOperationResult operationResult : result.getResults()) {
            assertThat(operationResult.isSuccessful(), is(true));
            assertThat(operationResult.getUpdated(), hasSize(1));
            assertThat(operationResult.getUpdated().get(0).getObject().getOid(), equalTo(root));
        }

        // should have received a *single* notification, since all changes were to the same shared folder
        verify(polaris.getNotifier(), times(1)).publishUpdate(root);
    }

    @Test
    public void shouldReturnResultsForCompletedOperationsEvenIfSomeFailed() throws InterruptedException {
        // construct a number of files in root
        String root = Identifiers.newRandomSharedFolder();

        TransformBatch batch = new TransformBatch(ImmutableList.of(
                new TransformBatchOperation(root, new InsertChild(Identifiers.newRandomObject(), ObjectType.FILE, "file_1")),
                new TransformBatchOperation(root, new InsertChild(Identifiers.newRandomObject(), ObjectType.FILE, "file_2")),
                new TransformBatchOperation(root, new InsertChild(Identifiers.newRandomObject(), ObjectType.FILE, "file_1"))
        ));

        // attempt to reinsert filename into root to create:
        // root -> (folder_1 -> filename, filename)
        TransformBatchResult result = given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(batch)
                .and()
                .when().post(PolarisTestServer.getTransformBatchURL())
                .then()
                .extract().response().as(TransformBatchResult.class);

        assertThat(result.getResults(), hasSize(3));

        TransformBatchOperationResult operationResult;

        // first result
        operationResult = result.getResults().get(0);
        assertThat(operationResult.isSuccessful(), is(true));
        assertThat(operationResult.getUpdated(), hasSize(1));
        assertThat(operationResult.getUpdated().get(0).getObject().getOid(), equalTo(root));

        // second result
        operationResult = result.getResults().get(1);
        assertThat(operationResult.isSuccessful(), is(true));
        assertThat(operationResult.getUpdated(), hasSize(1));
        assertThat(operationResult.getUpdated().get(0).getObject().getOid(), equalTo(root));

        // third result
        operationResult = result.getResults().get(2);
        assertThat(operationResult.isSuccessful(), is(false));
        assertThat(operationResult.getErrorCode(), equalTo(PolarisError.NAME_CONFLICT));

        // should have received a notification for the completed operation
        verify(polaris.getNotifier(), times(1)).publishUpdate(root);
    }

    @Test
    public void shouldAbortBatchEarlyAndReturnResultsForCompletedOperations() throws InterruptedException {
        // construct a number of files in root
        String root = Identifiers.newRandomSharedFolder();

        TransformBatch batch = new TransformBatch(ImmutableList.of(
                new TransformBatchOperation(root, new InsertChild(Identifiers.newRandomObject(), ObjectType.FILE, "file_1")),
                new TransformBatchOperation(root, new InsertChild(Identifiers.newRandomObject(), ObjectType.FILE, "file_1")),
                new TransformBatchOperation(root, new InsertChild(Identifiers.newRandomObject(), ObjectType.FILE, "file_2"))
        ));

        // attempt to reinsert filename into root to create:
        // root -> (folder_1 -> filename, filename)
        TransformBatchResult result = given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(batch)
                .and()
                .when().post(PolarisTestServer.getTransformBatchURL())
                .then()
                .extract().response().as(TransformBatchResult.class);

        assertThat(result.getResults(), hasSize(2));

        TransformBatchOperationResult operationResult;

        // first result
        operationResult = result.getResults().get(0);
        assertThat(operationResult.isSuccessful(), is(true));
        assertThat(operationResult.getUpdated(), hasSize(1));
        assertThat(operationResult.getUpdated().get(0).getObject().getOid(), equalTo(root));

        // second result
        operationResult = result.getResults().get(1);
        assertThat(operationResult.isSuccessful(), is(false));
        assertThat(operationResult.getErrorCode(), equalTo(PolarisError.NAME_CONFLICT));

        // should have received a notification for the completed operation
        verify(polaris.getNotifier(), times(1)).publishUpdate(root);
    }

    @Test
    public void shouldAbortBatchEarlyIfAccessChecksFailAndReturnResultsForCompletedOperations() throws InterruptedException {
        // construct a number of files in root
        String root = Identifiers.newRandomSharedFolder();

        TransformBatch batch = new TransformBatch(ImmutableList.of(
                new TransformBatchOperation(root, new InsertChild(Identifiers.newRandomObject(), ObjectType.FILE, "file_1")),
                new TransformBatchOperation(root, new InsertChild(Identifiers.newRandomObject(), ObjectType.FILE, "file_1")),
                new TransformBatchOperation(root, new InsertChild(Identifiers.newRandomObject(), ObjectType.FILE, "file_2"))
        ));

        // attempt to reinsert filename into root to create:
        // root -> (folder_1 -> filename, filename)
        TransformBatchResult result = given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(batch)
                .and()
                .when().post(PolarisTestServer.getTransformBatchURL())
                .then()
                .extract().response().as(TransformBatchResult.class);

        assertThat(result.getResults(), hasSize(2));

        TransformBatchOperationResult operationResult;

        // first result
        operationResult = result.getResults().get(0);
        assertThat(operationResult.isSuccessful(), is(true));
        assertThat(operationResult.getUpdated(), hasSize(1));
        assertThat(operationResult.getUpdated().get(0).getObject().getOid(), equalTo(root));

        // second result
        operationResult = result.getResults().get(1);
        assertThat(operationResult.isSuccessful(), is(false));
        assertThat(operationResult.getErrorCode(), equalTo(PolarisError.NAME_CONFLICT));

        // should have received a notification for the completed operation
        verify(polaris.getNotifier(), times(1)).publishUpdate(root);
    }
}