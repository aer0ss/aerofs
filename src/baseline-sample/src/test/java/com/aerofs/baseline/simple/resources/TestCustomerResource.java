package com.aerofs.baseline.simple.resources;

import com.aerofs.baseline.ids.Identifiers;
import com.aerofs.baseline.simple.ServerConfiguration;
import com.aerofs.baseline.simple.SimpleResource;
import com.aerofs.baseline.simple.Utilities;
import com.aerofs.baseline.simple.api.Customer;
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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public final class TestCustomerResource {

    static {
        RestAssured.config = Utilities.newRestAssuredConfig();
    }

    private static final String DEVICE = Identifiers.newRandomDevice();
    private static final String USERID = "test@aerofs.com";

    private final RequestSpecification verified = Utilities.newVerifiedAeroUserSpecification(DEVICE, USERID);

    @Rule
    public SimpleResource simpleResource = new SimpleResource();

    @Test
    public void updateNumberOfSeatsForExistingCustomer() throws IOException {
        // create the customer
        Customer original = new Customer(1, "customer0", "org0", 3);
        RestAssured
                .given()
                .spec(verified)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(original)
                .post(ServerConfiguration.CUSTOMERS_URL)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        List<Customer> customers;

        // there should be one (with the original values)
        customers = Utilities.getCustomers();
        assertThat(customers, hasSize(1));
        assertThat(customers, contains(original));

        // modify this existing customer
        Customer modified = new Customer(1, "customer0", "org0", 10);
        RestAssured
                .given()
                .spec(verified)
                .queryParam("seats", modified.seats)
                .post(Utilities.getCustomerURL(), "1")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        // it should have been modified and have the new values
        customers = Utilities.getCustomers();
        assertThat(customers, hasSize(1));
        assertThat(customers, contains(modified));
    }

    @Test
    public void shouldReturnBadRequestIfInvalidNumberOfSeatsAreSpecified() throws IOException {
        // create the customer
        Customer customer = new Customer(1, "customer0", "org0", 3);
        RestAssured
                .given()
                .spec(verified)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(customer)
                .post(ServerConfiguration.CUSTOMERS_URL)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        List<Customer> customers;

        // there should be one (with the original values)
        customers = Utilities.getCustomers();
        assertThat(customers, hasSize(1));
        assertThat(customers, contains(customer));

        // modify this existing customer
        // this should fail, because of an invalid number of seats
        RestAssured
                .given()
                .spec(verified)
                .queryParam("seats", 100)
                .post(Utilities.getCustomerURL(), "1")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // when we ask, we should still get the original result
        customers = Utilities.getCustomers();
        assertThat(customers, hasSize(1));
        assertThat(customers, contains(customer));
    }

    @Test
    public void shouldReturnForbiddenIfAttemptToModifyNumberOfSeatsAsUnverifiedUser() throws IOException {
        // create the customer
        Customer customer = new Customer(1, "customer0", "org0", 3);
        RestAssured
                .given()
                .spec(verified)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(customer)
                .post(ServerConfiguration.CUSTOMERS_URL)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        List<Customer> customers;

        // there should be one (with the original values)
        customers = Utilities.getCustomers();
        assertThat(customers, hasSize(1));
        assertThat(customers, contains(customer));

        // modify this existing customer
        // this should fail, because we're an unverified user
        RestAssured
                .given()
                .queryParam("seats", 6)
                .post(Utilities.getCustomerURL(), "1")
                .then()
                .statusCode(Response.Status.FORBIDDEN.getStatusCode());

        // when we ask, we should still get the original result
        customers = Utilities.getCustomers();
        assertThat(customers, hasSize(1));
        assertThat(customers, contains(customer));
    }

    @Test
    public void shouldReturnNotFoundIfAttemptToUpdateNumberOfSeatsForInvalidCustomer() throws Exception {
        // modify a customer that does not exist
        RestAssured
                .given()
                .spec(verified)
                .queryParam("seats", 5)
                .post(ServerConfiguration.CUSTOMERS_URL + "/1")
                .then()
                .statusCode(Response.Status.NOT_FOUND.getStatusCode());

        // no customers should have been added
        List<Customer> existing = Utilities.getCustomers();
        assertThat(existing, hasSize(0));
    }

    @Test
    public void shouldReturnCustomerInformationForExistingCustomer() throws Exception {
        // create the customer
        Customer created = new Customer(1, "customer0", "org0", 3);
        RestAssured
                .given()
                .spec(verified)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(created)
                .post(ServerConfiguration.CUSTOMERS_URL)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        // get info on this existing customer
        Customer existing = RestAssured
                .given()
                .spec(verified)
                .get(Utilities.getCustomerURL(), "1")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .as(Customer.class);

        // when we ask, we should still get the original result
        assertThat(existing, equalTo(created));
    }

    @Test
    public void shouldReturnCustomerInformationForExistingCustomerEvenIfUnverifiedUser() throws Exception {
        // create the customer
        Customer created = new Customer(1, "customer0", "org0", 3);
        RestAssured
                .given()
                .spec(verified)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(created)
                .post(ServerConfiguration.CUSTOMERS_URL)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        // get info on this existing customer
        // note that we are not verified
        Customer existing = RestAssured
                .given()
                .get(Utilities.getCustomerURL(), "1")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .as(Customer.class);

        // when we ask, we should still get the original result
        assertThat(existing, equalTo(created));
    }

    @Test
    public void shouldReturnNotFoundIfAttemptToGetInfoOnInvalidCustomer() throws Exception {
        // get info on a non-existent customer
        RestAssured
                .given()
                .spec(verified)
                .get(Utilities.getCustomerURL(), "1")
                .then()
                .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }
}