package com.aerofs.polaris.resources;

import com.aerofs.baseline.ids.Identifiers;
import com.aerofs.polaris.PolarisResource;
import com.aerofs.polaris.TestUtilities;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.batch.TransformBatch;
import com.aerofs.polaris.api.batch.TransformBatchOperation;
import com.aerofs.polaris.api.batch.TransformBatchOperationResult;
import com.aerofs.polaris.api.batch.TransformBatchResult;
import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.types.ObjectType;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.core.MediaType;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public final class TestTransformBatchResource {

    static {
        RestAssured.config = TestUtilities.newRestAssuredConfig();
    }

    private final RequestSpecification verified = TestUtilities.newVerifiedAeroUserSpecification(Identifiers.newRandomDevice(), "test@aerofs.com");

    @Rule
    public PolarisResource polarisResource = new PolarisResource();

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
                .spec(verified)
                .and()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).and().body(batch)
                .when().post(TestUtilities.getTransformBatchURL())
                .then()
                .extract().response().as(TransformBatchResult.class);

        assertThat(result.results, hasSize(3));

        for (TransformBatchOperationResult operationResult : result.results) {
            assertThat(operationResult.successful, is(true));
            assertThat(operationResult.updated, hasSize(1));
            assertThat(operationResult.updated.get(0).object.oid, equalTo(root));
        }
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
                .spec(verified)
                .and()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).and().body(batch)
                .when().post(TestUtilities.getTransformBatchURL())
                .then()
                .extract().response().as(TransformBatchResult.class);

        assertThat(result.results, hasSize(3));

        TransformBatchOperationResult operationResult;

        // first result
        operationResult = result.results.get(0);
        assertThat(operationResult.successful, is(true));
        assertThat(operationResult.updated, hasSize(1));
        assertThat(operationResult.updated.get(0).object.oid, equalTo(root));

        // second result
        operationResult = result.results.get(1);
        assertThat(operationResult.successful, is(true));
        assertThat(operationResult.updated, hasSize(1));
        assertThat(operationResult.updated.get(0).object.oid, equalTo(root));

        // third result
        operationResult = result.results.get(2);
        assertThat(operationResult.successful, is(false));
        assertThat(operationResult.errorCode, equalTo(PolarisError.NAME_CONFLICT));
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
                .spec(verified)
                .and()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).and().body(batch)
                .when().post(TestUtilities.getTransformBatchURL())
                .then()
                .extract().response().as(TransformBatchResult.class);

        assertThat(result.results, hasSize(2));

        TransformBatchOperationResult operationResult;

        // first result
        operationResult = result.results.get(0);
        assertThat(operationResult.successful, is(true));
        assertThat(operationResult.updated, hasSize(1));
        assertThat(operationResult.updated.get(0).object.oid, equalTo(root));

        // second result
        operationResult = result.results.get(1);
        assertThat(operationResult.successful, is(false));
        assertThat(operationResult.errorCode, equalTo(PolarisError.NAME_CONFLICT));
    }
}