package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.core.Identifiers;
import com.aerofs.polaris.PolarisTestServer;
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
import org.junit.rules.RuleChain;

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

    private final RequestSpecification verified = TestUtilities.newVerifiedAeroUserSpecification("test@aerofs.com", Identifiers.newRandomDevice());

    @Rule
    public RuleChain polaris = RuleChain.outerRule(new MySQLDatabase("test")).around(new PolarisTestServer());

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

        assertThat(result.getResults(), hasSize(3));

        for (TransformBatchOperationResult operationResult : result.getResults()) {
            assertThat(operationResult.isSuccessful(), is(true));
            assertThat(operationResult.getUpdated(), hasSize(1));
            assertThat(operationResult.getUpdated().get(0).getObject().getOid(), equalTo(root));
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
    }
}