package com.aerofs.polaris.logical;

import com.aerofs.polaris.PolarisResource;
import com.aerofs.polaris.TestSetup;
import com.aerofs.polaris.api.ObjectType;
import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.operation.MoveChild;
import com.aerofs.polaris.ids.Identifiers;
import com.google.common.net.HttpHeaders;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public final class TestLogicalObjectStore {

    private static final String OBJECTS_URI = "http://localhost:9999/objects/";

    static {
        RestAssured.config = TestSetup.newRestAssuredConfig();
    }

    @Rule
    public PolarisResource polarisResource = new PolarisResource();

    @Test
    public void shouldNotAllowObjectToBeInsertedMultipleTimesIntoDifferentTrees() throws InterruptedException {
        String root = Identifiers.newRandomSharedFolder();
        String fldr = Identifiers.newRandomObject();
        String file = Identifiers.newRandomObject();
        RequestSpecification verified = TestSetup.newVerifiedAeroUserSpecification("test@aerofs.com", Identifiers.newRandomDevice());

        // shf -> folder_1
        given().spec(verified)
               .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
               .body(new InsertChild(fldr, ObjectType.FOLDER, "folder_1"))
               .when()
               .post(OBJECTS_URI + root)
               .then()
               .assertThat()
               .statusCode(Response.Status.OK.getStatusCode())
               .body("updated[0].object.version", equalTo(1));

        // shf -> folder_1 -> file
        given().spec(verified)
               .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
               .body(new InsertChild(file, ObjectType.FILE, "file1"))
               .when()
               .post(OBJECTS_URI + fldr)
               .then()
               .assertThat()
               .statusCode(Response.Status.OK.getStatusCode())
               .body("updated[0].object.version", equalTo(1));

        // shf -> folder_1 -> file
        // shf -> file (X)
        given().spec(verified)
               .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
               .body(new InsertChild(file, ObjectType.FILE, "file2"))
               .when()
               .post(OBJECTS_URI + root)
               .then()
               .assertThat()
               .statusCode(Response.Status.CONFLICT.getStatusCode());
    }

    @Test
    public void shouldAllowObjectToBeInsertedMultipleTimesIntoDifferentTreesAsPartOfMove() throws InterruptedException {
        String root = Identifiers.newRandomSharedFolder();
        String fldr = Identifiers.newRandomObject();
        String file = Identifiers.newRandomObject();
        RequestSpecification verified = TestSetup.newVerifiedAeroUserSpecification("test@aerofs.com", Identifiers.newRandomDevice());

        // shf -> folder_1
        given().spec(verified)
               .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
               .body(new InsertChild(fldr, ObjectType.FOLDER, "folder_1"))
               .when()
               .post(OBJECTS_URI + root)
               .then()
               .assertThat()
               .statusCode(Response.Status.OK.getStatusCode())
               .body("updated[0].object.version", equalTo(1));

        // shf -> folder_1 -> file
        given().spec(verified)
               .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
               .body(new InsertChild(file, ObjectType.FILE, "file1"))
               .when()
               .post(OBJECTS_URI + fldr)
               .then()
               .assertThat()
               .statusCode(Response.Status.OK.getStatusCode())
               .body("updated[0].object.version", equalTo(1));

        // shf -> folder_1
        // shf -> file (via move - should be allowed)
        given().spec(verified)
               .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
               .body(new MoveChild(file, root, "file2"))
               .when()
               .post(OBJECTS_URI + fldr)
               .then()
               .assertThat()
               .statusCode(Response.Status.OK.getStatusCode())
               .body("updated[0].object.version", equalTo(2), "updated[1].object.version", equalTo(2));
    }
}