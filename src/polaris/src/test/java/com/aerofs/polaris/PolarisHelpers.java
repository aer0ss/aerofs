package com.aerofs.polaris;

import com.aerofs.auth.server.cert.AeroDeviceCert;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.polaris.api.PolarisModule;
import com.aerofs.polaris.api.operation.AppliedTransforms;
import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.operation.MoveChild;
import com.aerofs.polaris.api.operation.RemoveChild;
import com.aerofs.polaris.api.operation.UpdateContent;
import com.aerofs.polaris.api.types.ObjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
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
                        .jackson2ObjectMapperFactory((cls, charset) -> newPolarisMapper()));
    }

    public static ObjectMapper newPolarisMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new PolarisModule());
        return mapper;
    }

    public static RequestSpecification newAuthedAeroUserReqSpec(UserID user, DID device) {
        String userString = user.getString();
        String deviceString = device.toStringFormal();
        return new RequestSpecBuilder().addHeaders(
                ImmutableMap.of(
                        AUTHORIZATION, com.aerofs.auth.client.cert.AeroDeviceCert.getHeaderValue(userString, deviceString),
                        AERO_DNAME_HEADER, String.format("G=test.aerofs.com/CN=%s", AeroDeviceCert.getCertificateCName(userString, deviceString)),
                        AERO_VERIFY_HEADER, AERO_VERIFY_SUCCEEDED_HEADER_VALUE
                )).build();
    }

    //
    // file and folder operations
    //

    public static OID newFile(RequestSpecification authenticated, UniqueID parent, String filename) {
        OID file = OID.generate();
        newFileUsingOID(authenticated, parent, file, filename);
        return file;
    }

    public static void newFileUsingOID(RequestSpecification authenticated, UniqueID parent, UniqueID file, String filename) {
        newObject(authenticated, parent, file, filename, FILE).assertThat().statusCode(SC_OK);
    }

    public static OID newFolder(RequestSpecification authenticated, UniqueID parent, String folderName) {
        OID folder = OID.generate();
        newFolderUsingOID(authenticated, parent, folder, folderName);
        return folder;
    }

    public static void newFolderUsingOID(RequestSpecification authenticated, UniqueID parent, UniqueID folder, String folderName) {
        newObject(authenticated, parent, folder, folderName, FOLDER).assertThat().statusCode(SC_OK);
    }

    public static ValidatableResponse newObject(RequestSpecification authenticated, UniqueID parent, UniqueID child, String childName, ObjectType childObjectType) {
        return given()
                .spec(authenticated)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new InsertChild(child, childObjectType, childName))
                .and()
                .when().post(PolarisTestServer.getObjectURL(parent))
                .then();
    }

    public static void newFileContent(RequestSpecification authenticated, OID file, long localVersion, byte[] hash, long size, long mtime) {
        newContent(authenticated, file, localVersion, hash, size, mtime).assertThat().statusCode(SC_OK);
    }

    public static ValidatableResponse newContent(RequestSpecification authenticated, OID file, long localVersion, byte[] hash, long size, long mtime) {
        Preconditions.checkArgument(hash.length == 32, "invalid hash length %s", hash.length);
        return given()
                .spec(authenticated)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new UpdateContent(localVersion, hash, size, mtime))
                .and()
                .when().post(PolarisTestServer.getObjectURL(file))
                .then();
    }

    public static OID removeFileOrFolder(RequestSpecification authenticated, UniqueID parent, OID child) {
        OID folder = OID.generate();
        removeObject(authenticated, parent, child).assertThat().statusCode(SC_OK);
        return folder;
    }

    public static ValidatableResponse removeObject(RequestSpecification authenticated, UniqueID parent, OID child) {
        return given()
                .spec(authenticated)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new RemoveChild(child))
                .and()
                .when().post(PolarisTestServer.getObjectURL(parent))
                .then();
    }

    public static OID moveFileOrFolder(RequestSpecification authenticated, UniqueID currentParent, UniqueID newParent, OID child, String newChildName) {
        OID folder = OID.generate();
        moveObject(authenticated, currentParent, newParent, child, newChildName).assertThat().statusCode(SC_OK);
        return folder;
    }

    public static ValidatableResponse moveObject(RequestSpecification authenticated, UniqueID currentParent, UniqueID newParent, OID child, String newChildName) {
        return given()
                .spec(authenticated)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new MoveChild(child, newParent, newChildName))
                .and()
                .when().post(PolarisTestServer.getObjectURL(currentParent))
                .then();
    }

    public static void addLocation(RequestSpecification authentication, OID object, long version, DID device) {
        given()
                .spec(authentication)
                .and()
                .when().post(PolarisTestServer.getLocationURL(object, version, device))
                .then()
                .assertThat().statusCode(SC_NO_CONTENT);
    }

    public static void removeLocation(RequestSpecification authentication, OID object, long version, DID device) {
        given()
                .spec(authentication)
                .and()
                .when().delete(PolarisTestServer.getLocationURL(object, version, device))
                .then()
                .assertThat().statusCode(SC_NO_CONTENT);
    }

    public static ValidatableResponse getLocations(RequestSpecification authentication, OID object, long version) {
        return given()
                .spec(authentication)
                .and()
                .header(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .and()
                .when().get(PolarisTestServer.getLocationsURL(object, version))
                .then()
                .assertThat().statusCode(SC_OK)
                .and();
    }

    //
    // get changes
    //

    public static AppliedTransforms getTransforms(RequestSpecification authenticated, SID root, long since, int resultCount) {
        return given()
                .spec(authenticated)
                .and()
                .parameters("oid", root.toStringFormal(), "since", since, "count", resultCount)
                .and()
                .when().get(PolarisTestServer.getTransformsURL(root))
                .then()
                .extract().as(AppliedTransforms.class);
    }

    //
    // dump logical database
    //

    public static InputStream getTreeAsStream(UniqueID root) {
        return given()
                    .queryParam("root", root.toStringFormal()).and().queryParam(JSON_COMMAND_RESPONSE_ENTITY_PRETTY_PRINTING_QUERY_PARAMETER)
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
