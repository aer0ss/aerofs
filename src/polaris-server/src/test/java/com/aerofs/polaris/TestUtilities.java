package com.aerofs.polaris;

import com.aerofs.baseline.auth.SecurityContexts;
import com.aerofs.baseline.http.Headers;
import com.aerofs.baseline.ids.Identifiers;
import com.aerofs.polaris.api.operation.AppliedTransforms;
import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.operation.MoveChild;
import com.aerofs.polaris.api.operation.RemoveChild;
import com.aerofs.polaris.api.operation.UpdateContent;
import com.aerofs.polaris.api.types.ObjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HttpHeaders;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.config.ObjectMapperConfig;
import com.jayway.restassured.config.RestAssuredConfig;
import com.jayway.restassured.internal.mapper.ObjectMapperType;
import com.jayway.restassured.mapper.factory.Jackson2ObjectMapperFactory;
import com.jayway.restassured.response.ValidatableResponse;
import com.jayway.restassured.specification.RequestSpecification;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;

import static com.jayway.restassured.RestAssured.given;

public abstract class TestUtilities {

    //
    // request configuration
    //

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

    public static RequestSpecification newVerifiedAeroUserSpecification(String device, String user) {
        return new RequestSpecBuilder().addHeaders(
                ImmutableMap.of(
                        Headers.AERO_AUTHORIZATION_HEADER, String.format(Headers.AERO_AUTHORIZATION_HEADER_FORMAT, device, user),
                        Headers.DNAME_HEADER, newDNameHeader(device, user),
                        Headers.VERIFY_HEADER, Headers.VERIFY_HEADER_OK_VALUE
                )).build();
    }

    private static String newDNameHeader(String device, String user) {
        return String.format("G=test.aerofs.com/CN=%s", SecurityContexts.getCertificateCName(device, user));
    }

    //
    // file and folder operations
    //

    public static String newFile(RequestSpecification validDevice, String parent, String filename) {
        String file = Identifiers.newRandomObject();
        newObject(validDevice, parent, file, filename, ObjectType.FILE).assertThat().statusCode(Response.Status.OK.getStatusCode());
        return file;
    }


    public static String newFolder(RequestSpecification validDevice, String parent, String folderName) {
        String folder = Identifiers.newRandomObject();
        newObject(validDevice, parent, folder, folderName, ObjectType.FOLDER).assertThat().statusCode(Response.Status.OK.getStatusCode());
        return folder;
    }

    public static ValidatableResponse newObject(RequestSpecification validDevice, String parent, String child, String childName, ObjectType childObjectType) {
        return given()
                .spec(validDevice)
                .and()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .body(new InsertChild(child, childObjectType, childName))
                .when()
                .post(getObjectURL(parent))
                .then();
    }

    public static void newFileContent(RequestSpecification validDevice, String file, long localVersion, String hash, long size, long mtime) {
        newContent(validDevice, file, localVersion, hash, size, mtime).assertThat().statusCode(Response.Status.OK.getStatusCode());
    }

    public static ValidatableResponse newContent(RequestSpecification validDevice, String file, long localVersion, String hash, long size, long mtime) {
        return given()
                .spec(validDevice)
                .and()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .body(new UpdateContent(localVersion, hash, size, mtime))
                .when()
                .post(getObjectURL(file))
                .then();
    }

    public static String removeFileOrFolder(RequestSpecification validDevice, String parent, String child) {
        String folder = Identifiers.newRandomObject();
        removeObject(validDevice, parent, child).assertThat().statusCode(Response.Status.OK.getStatusCode());
        return folder;
    }

    public static ValidatableResponse removeObject(RequestSpecification validDevice, String parent, String child) {
        return given()
                .spec(validDevice)
                .and()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .body(new RemoveChild(child))
                .when()
                .post(getObjectURL(parent))
                .then();
    }

    public static String moveFileOrFolder(RequestSpecification validDevice, String parent, String newParent, String child, String newChildName) {
        String folder = Identifiers.newRandomObject();
        moveObject(validDevice, parent, newParent, child, newChildName).assertThat().statusCode(Response.Status.OK.getStatusCode());
        return folder;
    }

    public static ValidatableResponse moveObject(RequestSpecification validDevice, String parent, String newParent, String child, String newChildName) {
        return given()
                .spec(validDevice)
                .and()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .body(new MoveChild(child, newParent, newChildName))
                .when()
                .post(getObjectURL(parent))
                .then();
    }

    public static InputStream getTreeAsStream(String root) {
        return given()
                    .queryParam("root", root)
                    .queryParam(com.aerofs.baseline.Constants.JSON_PRETTY_PRINTING_QUERY_PARAMETER)
                    .post(ServerConfiguration.TREE_URL)
                    .then()
                    .assertThat()
                    .statusCode(Response.Status.OK.getStatusCode())
                    .extract()
                    .body()
                    .asInputStream();
    }

    //
    // method calls
    //

    public static AppliedTransforms getTransforms(RequestSpecification validDevice, String root, long since, int resultCount) {
        return given()
                .spec(validDevice)
                .and()
                .parameters("oid", root, "since", since, "count", resultCount)
                .when()
                .get(getTransformsURL(root))
                .then()
                .extract().as(AppliedTransforms.class);
    }

    public static String getObjectURL(String oid) {
        return ServerConfiguration.OBJECTS_URL + oid + "/";
    }

    public static String getTransformBatchURL() {
        return ServerConfiguration.BATCH_URL + "transforms/";
    }

    public static String getLocationBatchURL() {
        return ServerConfiguration.BATCH_URL + "locations/";
    }

    private static String getTransformsURL(String root) {
        return ServerConfiguration.TRANSFORMS_URL + root + "/";
    }

    private TestUtilities() {
        // to prevent instantiation by subclasses
    }

}