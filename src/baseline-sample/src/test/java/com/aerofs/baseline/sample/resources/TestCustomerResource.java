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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public final class TestCustomerResource {

    static {
        RestAssured.config = TestUtilities.newRestAssuredConfig();
    }

    private static final String DEVICE = Identifiers.newRandomDevice();
    private static final String USERID = "test@aerofs.com";

    private final RequestSpecification verified = TestUtilities.newVerifiedAeroUserSpecification(DEVICE, USERID);

    @Rule
    public RuleChain sampleServer = RuleChain.outerRule(new MySQLDatabase("test")).around(new SampleTestServer());

    @Test
    public void updateNumberOfSeatsForExistingCustomer() throws IOException {
        // create the customer
        Customer original = new Customer(1, "customer0", "org0", 3);
        RestAssured
                .given()
                .spec(verified)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(original)
                .post(TestUtilities.getCustomersURL())
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        List<Customer> customers;

        // there should be one (with the original values)
        customers = TestUtilities.getCustomers();
        assertThat(customers, hasSize(1));
        assertThat(customers, contains(original));

        // modify this existing customer
        Customer modified = new Customer(1, "customer0", "org0", 10);
        RestAssured
                .given()
                .spec(verified)
                .queryParam("seats", modified.seats)
                .post(TestUtilities.getCustomerURL(), "1")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        // it should have been modified and have the new values
        customers = TestUtilities.getCustomers();
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
                .post(TestUtilities.getCustomersURL())
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        List<Customer> customers;

        // there should be one (with the original values)
        customers = TestUtilities.getCustomers();
        assertThat(customers, hasSize(1));
        assertThat(customers, contains(customer));

        // modify this existing customer
        // this should fail, because of an invalid number of seats
        RestAssured
                .given()
                .spec(verified)
                .queryParam("seats", 100)
                .post(TestUtilities.getCustomerURL(), "1")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // when we ask, we should still get the original result
        customers = TestUtilities.getCustomers();
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
                .post(TestUtilities.getCustomersURL())
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        List<Customer> customers;

        // there should be one (with the original values)
        customers = TestUtilities.getCustomers();
        assertThat(customers, hasSize(1));
        assertThat(customers, contains(customer));

        // modify this existing customer
        // this should fail, because we're an unverified user
        RestAssured
                .given()
                .queryParam("seats", 6)
                .post(TestUtilities.getCustomerURL(), "1")
                .then()
                .statusCode(Response.Status.FORBIDDEN.getStatusCode());

        // when we ask, we should still get the original result
        customers = TestUtilities.getCustomers();
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
                .post(TestUtilities.getCustomerURL(), "1")
                .then()
                .statusCode(Response.Status.NOT_FOUND.getStatusCode());

        // no customers should have been added
        List<Customer> existing = TestUtilities.getCustomers();
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
                .post(TestUtilities.getCustomersURL())
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        // get info on this existing customer
        Customer existing = RestAssured
                .given()
                .spec(verified)
                .get(TestUtilities.getCustomerURL(), "1")
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
                .post(TestUtilities.getCustomersURL())
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        // get info on this existing customer
        // note that we are not verified
        Customer existing = RestAssured
                .given()
                .get(TestUtilities.getCustomerURL(), "1")
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
                .get(TestUtilities.getCustomerURL(), "1")
                .then()
                .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }
}