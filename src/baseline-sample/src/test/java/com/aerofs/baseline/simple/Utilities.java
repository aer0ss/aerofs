package com.aerofs.baseline.simple;

import com.aerofs.baseline.auth.SecurityContexts;
import com.aerofs.baseline.http.Headers;
import com.aerofs.baseline.simple.api.Customer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.config.ObjectMapperConfig;
import com.jayway.restassured.config.RestAssuredConfig;
import com.jayway.restassured.internal.mapper.ObjectMapperType;
import com.jayway.restassured.mapper.factory.Jackson2ObjectMapperFactory;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;

import java.io.IOException;
import java.util.List;

/**
 * Utility methods.
 */
public abstract class Utilities {

    /**
     * Return a rest-assured configuration that correctly
     * serializes and deserializes camel-case json fields.
     */
    public static RestAssuredConfig newRestAssuredConfig() {
        return RestAssured
                .config()
                .objectMapperConfig(ObjectMapperConfig
                        .objectMapperConfig()
                        .defaultObjectMapperType(ObjectMapperType.JACKSON_2)
                        .jackson2ObjectMapperFactory(
                                new Jackson2ObjectMapperFactory() {
                                    @Override
                                    public ObjectMapper create(Class aClass, String s) {
                                        ObjectMapper mapper = new ObjectMapper();
                                        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
                                        return mapper;
                                    }
                                }));
    }

    /**
     * Return a rest-assured request specification
     * that mimics a verified AeroFS user.
     */
    public static RequestSpecification newVerifiedAeroUserSpecification(String device, String user) {
        return new RequestSpecBuilder().addHeaders(
                ImmutableMap.of(
                        Headers.AERO_AUTHORIZATION_HEADER, String.format(Headers.AERO_AUTHORIZATION_HEADER_FORMAT, device, user),
                        Headers.DNAME_HEADER, newDNameHeader(device, user),
                        Headers.VERIFY_HEADER, Headers.VERIFY_HEADER_OK_VALUE
                )).build();
    }

    /**
     * Return the current list of customers.
     */
    public static List<Customer> getCustomers() throws IOException {
        Response response = RestAssured
                .given()
                .post(ServerConfiguration.DUMP_URL)
                .then()
                .statusCode(200)
                .extract()
                .response();

        return ServerConfiguration.OBJECT_MAPPER.readValue(response.getBody().asInputStream(), new TypeReference<List<Customer>>() { });
    }

    private static String newDNameHeader(String device, String user) {
        return String.format("G=test.aerofs.com/CN=%s", SecurityContexts.getCertificateCName(device, user));
    }

    public static String getCustomerURL() {
        return String.format("http://%s:%s/customers/{customer}", ServerConfiguration.APP.getHost(), ServerConfiguration.APP.getPort());
    }

    private Utilities() {
        // private to prevent instantiation
    }
}
