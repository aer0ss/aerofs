package com.aerofs.trifrost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.ObjectMapperConfig;
import com.jayway.restassured.config.RestAssuredConfig;
import com.jayway.restassured.internal.mapper.ObjectMapperType;

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
                .objectMapperConfig(
                        ObjectMapperConfig.objectMapperConfig()
                                .defaultObjectMapperType(ObjectMapperType.JACKSON_2)
                                .jackson2ObjectMapperFactory((aClass, s) -> {
                                    ObjectMapper mapper = new ObjectMapper();
                                    mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
                                    return mapper;
                                }));
    }
}
