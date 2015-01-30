package com.aerofs.polaris;

import com.aerofs.auth.server.cert.AeroDeviceCert;
import com.aerofs.ids.core.Identifiers;
import com.aerofs.polaris.api.operation.AppliedTransforms;
import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.operation.MoveChild;
import com.aerofs.polaris.api.operation.RemoveChild;
import com.aerofs.polaris.api.operation.UpdateContent;
import com.aerofs.polaris.api.types.ObjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HttpHeaders;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.config.ObjectMapperConfig;
import com.jayway.restassured.config.RestAssuredConfig;
import com.jayway.restassured.response.ValidatableResponse;
import com.jayway.restassured.specification.RequestSpecification;

import javax.ws.rs.core.Response;
import java.io.InputStream;

import static com.aerofs.auth.server.cert.AeroDeviceCert.AERO_DNAME_HEADER;
import static com.aerofs.auth.server.cert.AeroDeviceCert.AERO_VERIFY_HEADER;
import static com.aerofs.auth.server.cert.AeroDeviceCert.AERO_VERIFY_SUCCEEDED_HEADER_VALUE;
import static com.aerofs.baseline.Constants.JSON_COMMAND_RESPONSE_ENTITY_PRETTY_PRINTING_QUERY_PARAMETER;
import static com.aerofs.polaris.api.types.ObjectType.FILE;
import static com.aerofs.polaris.api.types.ObjectType.FOLDER;
import static com.fasterxml.jackson.databind.PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.internal.mapper.ObjectMapperType.JACKSON_2;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;

public abstract class PolarisHelpers {

    //
    // rest-assured configuration
    //

    public static RestAssuredConfig newRestAssuredConfig() {
        return RestAssured
                .config()
                .objectMapperConfig(ObjectMapperConfig
                        .objectMapperConfig()
                        .defaultObjectMapperType(JACKSON_2)
                        .jackson2ObjectMapperFactory((cls, charset) -> newCamelCaseMapper()));
    }

    public static ObjectMapper newCamelCaseMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
        return mapper;
    }

    public static RequestSpecification newAuthedAeroUserReqSpec(String userid, String device) {
        return new RequestSpecBuilder().addHeaders(
                ImmutableMap.of(
                        AUTHORIZATION, com.aerofs.auth.client.cert.AeroDeviceCert.getHeaderValue(userid, device),
                        AERO_DNAME_HEADER, String.format("G=test.aerofs.com/CN=%s", AeroDeviceCert.getCertificateCName(userid, device)),
                        AERO_VERIFY_HEADER, AERO_VERIFY_SUCCEEDED_HEADER_VALUE
                )).build();
    }

    //
    // file and folder operations
    //

    public static String newFile(RequestSpecification authenticated, String parentOid, String filename) {
        String file = Identifiers.newRandomObject();
        newObject(authenticated, parentOid, file, filename, FILE).assertThat().statusCode(SC_OK);
        return file;
    }

    public static String newFolder(RequestSpecification authenticated, String parentOid, String folderName) {
        String folder = Identifiers.newRandomObject();
        newObject(authenticated, parentOid, folder, folderName, FOLDER).assertThat().statusCode(SC_OK);
        return folder;
    }

    public static ValidatableResponse newObject(RequestSpecification authenticated, String parentOid, String childOid, String childName, ObjectType childObjectType) {
        return given()
                .spec(authenticated)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new InsertChild(childOid, childObjectType, childName))
                .and()
                .when().post(PolarisTestServer.getObjectURL(parentOid))
                .then();
    }

    public static void newFileContent(RequestSpecification authenticated, String file, long localVersion, String hash, long size, long mtime) {
        newContent(authenticated, file, localVersion, hash, size, mtime).assertThat().statusCode(SC_OK);
    }

    public static ValidatableResponse newContent(RequestSpecification authenticated, String file, long localVersion, String hash, long size, long mtime) {
        return given()
                .spec(authenticated)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new UpdateContent(localVersion, hash, size, mtime))
                .and()
                .when().post(PolarisTestServer.getObjectURL(file))
                .then();
    }

    public static String removeFileOrFolder(RequestSpecification authenticated, String parentOid, String childOid) {
        String folder = Identifiers.newRandomObject();
        removeObject(authenticated, parentOid, childOid).assertThat().statusCode(SC_OK);
        return folder;
    }

    public static ValidatableResponse removeObject(RequestSpecification authenticated, String parentOid, String childOid) {
        return given()
                .spec(authenticated)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new RemoveChild(childOid))
                .and()
                .when().post(PolarisTestServer.getObjectURL(parentOid))
                .then();
    }

    public static String moveFileOrFolder(RequestSpecification authenticated, String parentOid, String newParentOid, String childOid, String newChildName) {
        String folder = Identifiers.newRandomObject();
        moveObject(authenticated, parentOid, newParentOid, childOid, newChildName).assertThat().statusCode(SC_OK);
        return folder;
    }

    public static ValidatableResponse moveObject(RequestSpecification authenticated, String parentOid, String newParentOid, String childOid, String newChildName) {
        return given()
                .spec(authenticated)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new MoveChild(childOid, newParentOid, newChildName))
                .and()
                .when().post(PolarisTestServer.getObjectURL(parentOid))
                .then();
    }

    public static void addLocation(RequestSpecification authentication, String oid, long version, String device) {
        given()
                .spec(authentication)
                .and()
                .when().post(PolarisTestServer.getLocationURL(oid, version, device))
                .then()
                .assertThat().statusCode(SC_NO_CONTENT);
    }

    public static void removeLocation(RequestSpecification authentication, String oid, long version, String device) {
        given()
                .spec(authentication)
                .and()
                .when().delete(PolarisTestServer.getLocationURL(oid, version, device))
                .then()
                .assertThat().statusCode(SC_NO_CONTENT);
    }

    public static ValidatableResponse getLocations(RequestSpecification authentication, String oid, long version) {
        return given()
                .spec(authentication)
                .and()
                .header(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .and()
                .when().get(PolarisTestServer.getLocationsURL(oid, version))
                .then()
                .assertThat().statusCode(SC_OK)
                .and();
    }

    //
    // get changes
    //

    public static AppliedTransforms getTransforms(RequestSpecification authenticated, String rootOid, long since, int resultCount) {
        return given()
                .spec(authenticated)
                .and()
                .parameters("oid", rootOid, "since", since, "count", resultCount)
                .and()
                .when().get(PolarisTestServer.getTransformsURL(rootOid))
                .then()
                .extract().as(AppliedTransforms.class);
    }

    //
    // dump logical database
    //

    public static InputStream getTreeAsStream(String rootOid) {
        return given()
                    .queryParam("root", rootOid).and().queryParam(JSON_COMMAND_RESPONSE_ENTITY_PRETTY_PRINTING_QUERY_PARAMETER)
                    .and()
                    .when().post(PolarisTestServer.getTreeUrl())
                    .then()
                    .assertThat()
                    .statusCode(Response.Status.OK.getStatusCode())
                    .extract()
                    .body()
                    .asInputStream();
    }

    private PolarisHelpers() {
        // to prevent instantiation by subclasses
    }
}
