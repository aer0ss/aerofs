package com.aerofs.baseline.sample.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.baseline.sample.SampleTestServer;
import com.aerofs.baseline.sample.TestUtilities;
import com.aerofs.baseline.sample.api.Customer;
import com.aerofs.ids.core.Identifiers;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

public final class TestCustomersResource {

    static {
        RestAssured.config = TestUtilities.newRestAssuredConfig();
    }

    private static final String DEVICE = Identifiers.newRandomDevice();
    private static final String USERID = "test@aerofs.com";

    private final RequestSpecification verified = TestUtilities.newVerifiedAeroUserSpecification(DEVICE, USERID);

    @Rule
    public RuleChain sampleServer = RuleChain.outerRule(new MySQLDatabase("test")).around(new SampleTestServer());

    @Test
    public void shouldAddNewCustomer() throws IOException {
        Customer customer = new Customer(1, "customer0", "org0", 3);
        RestAssured
                .given()
                .spec(verified)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(customer)
                .post(TestUtilities.getCustomersURL())
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        List<Customer> existing = TestUtilities.getCustomers();

        // there should be one
        assertThat(existing, hasSize(1));
        assertThat(existing, contains(customer));
    }

    @Test
    public void shouldReturnForbiddenIfAttemptToCreateCustomerAndUnverifiedUser() throws IOException {
        RestAssured
                .given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(new Customer(1, "customer0", "org0", 3))
                .post(TestUtilities.getCustomersURL())
                .then()
                .statusCode(Response.Status.FORBIDDEN.getStatusCode());

        List<Customer> existing = TestUtilities.getCustomers();

        // there should be none
        assertThat(existing, hasSize(0));
    }

    @Test
    public void shouldThrowBadRequestIfCustomerObjectHasInvalidValues() throws IOException {
        Customer customer = new Customer(1, "customer0", "org0", 30); // exceeds max seats
        RestAssured
                .given()
                .spec(verified)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(customer)
                .post(TestUtilities.getCustomersURL())
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        List<Customer> existing = TestUtilities.getCustomers();

        // there should be none
        assertThat(existing, hasSize(0));
    }
}