package com.aerofs.polaris.resources;

import com.aerofs.baseline.ids.Identifiers;
import com.aerofs.polaris.PolarisResource;
import com.aerofs.polaris.TestUtilities;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.types.ObjectType;
import com.google.common.net.HttpHeaders;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public final class TestObjectResource {

    static {
        RestAssured.config = TestUtilities.newRestAssuredConfig();
    }

    private final RequestSpecification verified = TestUtilities.newVerifiedAeroUserSpecification("test@aerofs.com", Identifiers.newRandomDevice());

    @Rule
    public PolarisResource polarisResource = new PolarisResource();

    @Test
    public void shouldNotAllowObjectToBeInsertedMultipleTimesIntoDifferentTrees() throws InterruptedException {
        // construct root -> folder_1 -> filename
        String root = Identifiers.newRandomSharedFolder();
        String folder = TestUtilities.newFolder(verified, root, "folder_1");
        String file = TestUtilities.newFile(verified, folder, "filename");

        // attempt to reinsert filename into root to create:
        // root -> (folder_1 -> filename, filename)
        TestUtilities
                .newObject(verified, root, file, "file2", ObjectType.FILE)
                .assertThat()
                .statusCode(Response.Status.CONFLICT.getStatusCode());
    }

    @Test
    public void shouldAllowObjectToBeInsertedMultipleTimesIntoDifferentTreesAsPartOfMove() throws InterruptedException {
        // construct root -> folder_1 -> filename
        String root = Identifiers.newRandomSharedFolder();
        String folder = TestUtilities.newFolder(verified, root, "folder_1");
        String file = TestUtilities.newFile(verified, folder, "filename");

        // move filename from folder_1 to root to create:
        // root -> (folder_1, filename)
        TestUtilities
                .moveObject(verified, folder, root, file, "filename")
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("updated[0].object.version", equalTo(2), "updated[1].object.version", equalTo(2)); // two objects are updated
    }

    @Test
    public void shouldTreatMoveOfObjectIntoSameParentButWithADifferentNameAsARename() throws InterruptedException {
        // construct root -> folder_1 -> filename_original
        String root = Identifiers.newRandomSharedFolder();
        String folder = TestUtilities.newFolder(verified, root, "folder_1");
        String file = TestUtilities.newFile(verified, folder, "filename_original");

        // rename filename_original -> filename_modified
        // root -> folder_1 -> filename_modified
        TestUtilities
                .moveObject(verified, folder, folder, file, "filename_modified")
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("updated[0].object.oid", equalTo(folder), "updated[0].object.version", equalTo(2));
    }

    @Test
    public void shouldReturnANameConflictIfDifferentFilesWithSameNameAttemptedToBeInsertedIntoSameParent() throws InterruptedException {
        // construct root -> filename
        String root = Identifiers.newRandomSharedFolder();
        TestUtilities.newFile(verified, root, "filename");

        // attempt to create a new filename with the same name in root
        TestUtilities
                .newObject(verified, root, Identifiers.newRandomObject(), "filename", ObjectType.FILE)
                .assertThat()
                .statusCode(Response.Status.CONFLICT.getStatusCode())
                .body("error_code", equalTo(PolarisError.NAME_CONFLICT.code()));
    }

    // update to a deleted object? (what is this about rooting?)
}