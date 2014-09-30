package com.aerofs.polaris;

import com.aerofs.baseline.http.Headers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.config.ObjectMapperConfig;
import com.jayway.restassured.config.RestAssuredConfig;
import com.jayway.restassured.internal.mapper.ObjectMapperType;
import com.jayway.restassured.mapper.factory.Jackson2ObjectMapperFactory;
import com.jayway.restassured.specification.RequestSpecification;

public abstract class TestSetup {

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

    public static RequestSpecification newVerifiedAeroUserSpecification(String user, String device) {
        return new RequestSpecBuilder().addHeaders(
                ImmutableMap.of(
                        Headers.AERO_USERID_HEADER, user,
                        Headers.AERO_DEVICE_HEADER, device,
                        Headers.DNAME_HEADER, newDNameHeader(user, device),
                        Headers.VERIFY_HEADER, Headers.VERIFY_HEADER_OK_VALUE
        )).build();
    }

    private static String newDNameHeader(String user, String device) {
        return "G=aerofs.com/CN=FIXME";
    }

    private TestSetup() {
        // to prevent instantiation by subclasses
    }
}
