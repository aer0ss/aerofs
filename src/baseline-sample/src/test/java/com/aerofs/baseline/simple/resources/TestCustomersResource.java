package com.aerofs.baseline.simple.resources;

import com.aerofs.baseline.simple.ServerConfiguration;
import com.aerofs.baseline.simple.SimpleResource;
import com.aerofs.baseline.simple.Utilities;
import com.aerofs.baseline.simple.api.Customer;
import com.aerofs.ids.core.Identifiers;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

public final class TestCustomersResource {

    static {
        RestAssured.config = Utilities.newRestAssuredConfig();
    }

    private static final String DEVICE = Identifiers.newRandomDevice();
    private static final String USERID = "test@aerofs.com";

    private final RequestSpecification verified = Utilities.newVerifiedAeroUserSpecification(DEVICE, USERID);

    @Rule
    public SimpleResource simpleResource = new SimpleResource();

    @Test
    public void shouldAddNewCustomer() throws IOException {
        Customer customer = new Customer(1, "customer0", "org0", 3);
        RestAssured
                .given()
                .spec(verified)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(customer)
                .post(ServerConfiguration.CUSTOMERS_URL)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        List<Customer> existing = Utilities.getCustomers();

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
                .post(ServerConfiguration.CUSTOMERS_URL)
                .then()
                .statusCode(Response.Status.FORBIDDEN.getStatusCode());

        List<Customer> existing = Utilities.getCustomers();

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
                .post(ServerConfiguration.CUSTOMERS_URL)
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        List<Customer> existing = Utilities.getCustomers();

        // there should be none
        assertThat(existing, hasSize(0));
    }
}