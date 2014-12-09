package com.aerofs.baseline.simple.commands;

import com.aerofs.baseline.simple.ServerConfiguration;
import com.aerofs.baseline.simple.SimpleResource;
import com.aerofs.baseline.simple.Utilities;
import com.aerofs.baseline.simple.api.Customer;
import com.aerofs.ids.core.Identifiers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.Rule;
import org.junit.Test;

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
        RestAssured.config = Utilities.newRestAssuredConfig();
    }

    private static final String DEVICE = Identifiers.newRandomDevice();
    private static final String USERID = "test@aerofs.com";

    private final RequestSpecification verified = Utilities.newVerifiedAeroUserSpecification(DEVICE, USERID);

    @Rule
    public SimpleResource simpleResource = new SimpleResource();

    @Test
    public void shouldDumpEmptyListIfNoCustomersExist() {
        List<Customer> existing = RestAssured
                .given()
                .post(ServerConfiguration.DUMP_URL)
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
                .post(ServerConfiguration.CUSTOMERS_URL)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        // add customer 1
        Customer customer1 = new Customer(2, "customer1", "org1", 5);
        RestAssured
                .given()
                .spec(verified)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(customer1)
                .post(ServerConfiguration.CUSTOMERS_URL)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        // dump the list of customers
        com.jayway.restassured.response.Response response = RestAssured
                .given()
                .post(ServerConfiguration.DUMP_URL)
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .response();

        List<Customer> existing = ServerConfiguration.OBJECT_MAPPER.readValue(response.getBody().asInputStream(), new TypeReference<List<Customer>>() { });

        // there should be two
        assertThat(existing, hasSize(2));
        assertThat(existing, containsInAnyOrder(customer0, customer1));
    }
}