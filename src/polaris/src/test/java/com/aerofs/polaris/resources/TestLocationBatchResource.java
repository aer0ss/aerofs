package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.core.Identifiers;
import com.aerofs.polaris.PolarisTestServer;
import com.aerofs.polaris.TestUtilities;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.batch.LocationBatch;
import com.aerofs.polaris.api.batch.LocationBatchOperation;
import com.aerofs.polaris.api.batch.LocationBatchOperationResult;
import com.aerofs.polaris.api.batch.LocationBatchResult;
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

public final class TestLocationBatchResource {

    static {
        RestAssured.config = TestUtilities.newRestAssuredConfig();
    }

    private final String device = Identifiers.newRandomDevice();
    private final RequestSpecification verified = TestUtilities.newVerifiedAeroUserSpecification(device, "test@aerofs.com");

    @Rule
    public RuleChain polaris = RuleChain.outerRule(new MySQLDatabase("test")).around(new PolarisTestServer());

    @Test
    public void shouldSuccessfullyCompleteAllOperationsInBatch() throws InterruptedException {
        String root = Identifiers.newRandomSharedFolder();
        String object0 = TestUtilities.newFile(verified, root, "file0");
        String object1 = TestUtilities.newFile(verified, root, "file1");
        String object2 = TestUtilities.newFile(verified, root, "file2");

        LocationBatch batch = new LocationBatch(ImmutableList.of(
                new LocationBatchOperation(object0, 0, device, LocationBatchOperation.LocationUpdateType.INSERT),
                new LocationBatchOperation(object1, 0, device, LocationBatchOperation.LocationUpdateType.INSERT),
                new LocationBatchOperation(object2, 0, device, LocationBatchOperation.LocationUpdateType.INSERT)
        ));

        // attempt to add a number of locations
        LocationBatchResult result = given()
                .spec(verified)
                .and()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).and().body(batch)
                .when().post(TestUtilities.getLocationBatchURL())
                .then()
                .extract().response().as(LocationBatchResult.class);

        assertThat(result.getResults(), hasSize(3));

        for (LocationBatchOperationResult operationResult : result.getResults()) {
            assertThat(operationResult.isSuccessful(), is(true));
        }
    }

    @Test
    public void shouldReturnResultsForCompletedOperationsEvenIfSomeFailed() throws InterruptedException {
        // construct a number of files in root
        String root = Identifiers.newRandomSharedFolder();
        String object0 = TestUtilities.newFile(verified, root, "file0");
        String object1 = TestUtilities.newFile(verified, root, "file1");

        LocationBatch batch = new LocationBatch(ImmutableList.of(
                new LocationBatchOperation(object0, 0, device, LocationBatchOperation.LocationUpdateType.INSERT),
                new LocationBatchOperation(object1, 0, device, LocationBatchOperation.LocationUpdateType.INSERT),
                new LocationBatchOperation(Identifiers.newRandomDevice(), 0, device, LocationBatchOperation.LocationUpdateType.INSERT) // try to add a location for a random device
        ));

        // attempt to add a number of locations
        LocationBatchResult result = given()
                .spec(verified)
                .and()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).and().body(batch)
                .when().post(TestUtilities.getLocationBatchURL())
                .then()
                .extract().response().as(LocationBatchResult.class);

        assertThat(result.getResults(), hasSize(3));

        LocationBatchOperationResult operationResult;

        // first result
        operationResult = result.getResults().get(0);
        assertThat(operationResult.isSuccessful(), is(true));

        // second result
        operationResult = result.getResults().get(1);
        assertThat(operationResult.isSuccessful(), is(true));

        // third result
        operationResult = result.getResults().get(2);
        assertThat(operationResult.isSuccessful(), is(false));
        assertThat(operationResult.getErrorCode(), equalTo(PolarisError.NO_SUCH_OBJECT));
    }

    @Test
    public void shouldAbortBatchEarlyAndReturnResultsForCompletedOperations() throws InterruptedException {
        // construct a number of files in root
        String root = Identifiers.newRandomSharedFolder();
        String object0 = TestUtilities.newFile(verified, root, "file0");
        String object1 = TestUtilities.newFile(verified, root, "file1");

        LocationBatch batch = new LocationBatch(ImmutableList.of(
                new LocationBatchOperation(object0, 0, device, LocationBatchOperation.LocationUpdateType.INSERT),
                new LocationBatchOperation(Identifiers.newRandomDevice(), 0, device, LocationBatchOperation.LocationUpdateType.INSERT), // try to add a location for a random device
                new LocationBatchOperation(object1, 0, device, LocationBatchOperation.LocationUpdateType.INSERT)
        ));

        // attempt to add a number of locations
        LocationBatchResult result = given()
                .spec(verified)
                .and()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).and().body(batch)
                .when().post(TestUtilities.getLocationBatchURL())
                .then()
                .extract().response().as(LocationBatchResult.class);

        assertThat(result.getResults(), hasSize(2)); // the batch aborts early

        LocationBatchOperationResult operationResult;

        // first result
        operationResult = result.getResults().get(0);
        assertThat(operationResult.isSuccessful(), is(true));

        // second result
        operationResult = result.getResults().get(1);
        assertThat(operationResult.isSuccessful(), is(false));
        assertThat(operationResult.getErrorCode(), equalTo(PolarisError.NO_SUCH_OBJECT));
    }
}