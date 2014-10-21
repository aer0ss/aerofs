package com.aerofs.polaris.resources;

import com.aerofs.baseline.ids.Identifiers;
import com.aerofs.polaris.Constants;
import com.aerofs.polaris.PolarisResource;
import com.aerofs.polaris.TestUtilities;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.types.ObjectType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public final class TestObjectResource {

    static {
        RestAssured.config = TestUtilities.newRestAssuredConfig();
    }

    private final RequestSpecification verified = TestUtilities.newVerifiedAeroUserSpecification(Identifiers.newRandomDevice(), "test@aerofs.com");
    private final ObjectMapper mapper = new ObjectMapper();

    @Rule
    public PolarisResource polarisResource = new PolarisResource();

    @Test
    public void shouldProperlyCreateObjectTree() throws Exception {
        String root = Identifiers.newRandomSharedFolder();
        String a = TestUtilities.newFolder(verified, root, "A");
        String b = TestUtilities.newFolder(verified, a, "B");
        String c = TestUtilities.newFolder(verified, b, "C");
        TestUtilities.newFile(verified, c, "f1");

        checkTreeState(root, "tree/shouldProperlyCreateObjectTree.json");
    }

    @Test
    public void shouldNotAllowObjectToBeInsertedMultipleTimesIntoDifferentTrees() throws Exception {
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

        checkTreeState(root, "tree/shouldNotAllowObjectToBeInsertedMultipleTimesIntoDifferentTrees.json");
    }

    @Test
    public void shouldAllowObjectToBeInsertedMultipleTimesIntoDifferentTreesAsPartOfMove() throws Exception {
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


        checkTreeState(root, "tree/shouldAllowObjectToBeInsertedMultipleTimesIntoDifferentTreesAsPartOfMove.json");
    }

    @Test
    public void shouldTreatMoveOfObjectIntoSameParentButWithADifferentNameAsARename() throws Exception {
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

        checkTreeState(root, "tree/shouldTreatMoveOfObjectIntoSameParentButWithADifferentNameAsARename.json");
    }

    @Test
    public void shouldNotAllowRenameIfItWouldCauseANameConflict() throws Exception {
        // construct root -> folder_1 -> filename_original
        String root = Identifiers.newRandomSharedFolder();
        String file = TestUtilities.newFile(verified, root, "name2");
        TestUtilities.newFolder(verified, root, "name1");

        // rename name2 -> name1
        // should fail, because name1 already exists
        TestUtilities
                .moveObject(verified, root, root, file, "name1")
                .assertThat()
                .statusCode(Response.Status.CONFLICT.getStatusCode());


        checkTreeState(root, "tree/shouldNotAllowRenameIfItWouldCauseANameConflict.json");
    }

    @Test
    public void shouldReturnANameConflictIfDifferentFilesWithSameNameAttemptedToBeInsertedIntoSameParent() throws Exception {
        // construct root -> filename
        String root = Identifiers.newRandomSharedFolder();
        TestUtilities.newFile(verified, root, "filename");

        // attempt to create a new filename with the same name in root
        TestUtilities
                .newObject(verified, root, Identifiers.newRandomObject(), "filename", ObjectType.FILE)
                .assertThat()
                .statusCode(Response.Status.CONFLICT.getStatusCode())
                .body("error_code", equalTo(PolarisError.NAME_CONFLICT.code()));


        checkTreeState(root, "tree/shouldReturnANameConflictIfDifferentFilesWithSameNameAttemptedToBeInsertedIntoSameParent.json");
    }

    @Test
    public void shouldAllowUpdateToADeletedObject() throws Exception {
        // construct root -> filename
        String root = Identifiers.newRandomSharedFolder();
        String file = TestUtilities.newFile(verified, root, "filename");

        // update the content for the file
        TestUtilities
                .newContent(verified, file, 0, "HASH", 100, 1024)
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode()); // why is version 0?

        // now, delete the file
        TestUtilities
                .removeObject(verified, root, file)
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode());

        // verify that I can still update it
        TestUtilities
                .newContent(verified, file, 1, "HASH-2", 102, 1025)
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode());

        checkTreeState(Constants.NO_ROOT, "tree/shouldAllowUpdateToADeletedObject_0000.json");
    }

    @Test
    public void shouldAllowMoveFromDeletedParentToNonDeletedParent() throws Exception {
        // construct root -> folder_1 -> folder_1_1 -> file
        //           root -> folder_2
        String root = Identifiers.newRandomSharedFolder();
        String folder1 = TestUtilities.newFolder(verified, root, "folder_1");
        String folder11 = TestUtilities.newFolder(verified, folder1, "folder_1_1");
        String file = TestUtilities.newFile(verified, folder11, "file");
        TestUtilities.newFolder(verified, root, "folder_2");

        // now, delete folder_1
        TestUtilities
                .removeObject(verified, root, folder1)
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode());

        // check that I can move the file
        TestUtilities
                .moveObject(verified, folder11, root, file, "file")
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode());

        // and even its containing folder
        TestUtilities
                .moveObject(verified, folder1, root, folder11, "folder_1_1")
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode());

        checkTreeState(root, "tree/shouldAllowMoveFromDeletedParentToNonDeletedParent.json");
        checkTreeState(Constants.NO_ROOT, "tree/shouldAllowMoveFromDeletedParentToNonDeletedParent_0000.json");
    }

    @Test
    public void shouldAllowTwoObjectsWithTheSameNameToBeRemoved() throws Exception {

    }

    private void checkTreeState(String root, String json) throws IOException {
        JsonNode actual = getActualTree(root);
        JsonNode wanted = getWantedTree(root, json);

        assertThat(actual, equalTo(wanted));
    }

    private JsonNode getActualTree(String root) throws IOException {
        return mapper.readTree(TestUtilities.getTreeAsStream(root));
    }

    private JsonNode getWantedTree(String root, String resourcePath) throws IOException {
        return mapper.createObjectNode().set(root, mapper.readTree(Resources.getResource(resourcePath)));
    }
}
