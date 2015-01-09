package com.aerofs.baseline.sample.commands;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.baseline.sample.SampleTestServer;
import com.aerofs.baseline.sample.TestUtilities;
import com.aerofs.baseline.sample.api.Customer;
import com.aerofs.ids.core.Identifiers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

@SuppressWarnings("unchecked")
public final class TestDumpCommand {

    static {
        RestAssured.config = TestUtilities.newRestAssuredConfig();
    }

    private static final String DEVICE = Identifiers.newRandomDevice();
    private static final String USERID = "test@aerofs.com";

    private final RequestSpecification verified = TestUtilities.newVerifiedAeroUserSpecification(DEVICE, USERID);

    @Rule
    public RuleChain sampleServer = RuleChain.outerRule(new MySQLDatabase("test")).around(new SampleTestServer());

    @Test
    public void shouldDumpEmptyListIfNoCustomersExist() {
        List<Customer> existing = RestAssured
                .given()
                .post(TestUtilities.getDumpURL())
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .as(List.class);

        assertThat(existing, hasSize(0));
    }

    @Test
    public void shouldDumpCustomers() throws IOException {

        // add customer 0
        Customer customer0 = new Customer(1, "customer0", "org0", 3);
        RestAssured
                .given()
                .spec(verified)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(customer0)
                .post(TestUtilities.getCustomersURL())
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        // add customer 1
        Customer customer1 = new Customer(2, "customer1", "org1", 5);
        RestAssured
                .given()
                .spec(verified)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(customer1)
                .post(TestUtilities.getCustomersURL())
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        // dump the list of customers
        com.jayway.restassured.response.Response response = RestAssured
                .given()
                .post(TestUtilities.getDumpURL())
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .response();

        List<Customer> existing = TestUtilities.OBJECT_MAPPER.readValue(response.getBody().asInputStream(), new TypeReference<List<Customer>>() { });

        // there should be two
        assertThat(existing, hasSize(2));
        assertThat(existing, containsInAnyOrder(customer0, customer1));
    }
}