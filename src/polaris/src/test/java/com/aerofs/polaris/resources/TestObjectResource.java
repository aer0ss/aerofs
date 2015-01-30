package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.core.Identifiers;
import com.aerofs.polaris.Constants;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.PolarisTestServer;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.types.ObjectType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.apache.http.HttpStatus;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.IOException;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.jayway.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public final class TestObjectResource {

    static {
        RestAssured.config = PolarisHelpers.newRestAssuredConfig();
    }

    private static final String USERID = "test@aerofs.com";
    private static final String DEVICE = Identifiers.newRandomDevice();
    private static final RequestSpecification AUTHENTICATED = PolarisHelpers.newAuthedAeroUserReqSpec(USERID, DEVICE);

    private final MySQLDatabase database = new MySQLDatabase("test");
    private final PolarisTestServer polaris = new PolarisTestServer();

    @Rule
    public RuleChain chain = RuleChain.outerRule(database).around(polaris);

    @Test
    public void shouldProperlyCreateObjectTree() throws Exception {
        String root = Identifiers.newRandomSharedFolder();
        String a = PolarisHelpers.newFolder(AUTHENTICATED, root, "A");
        String b = PolarisHelpers.newFolder(AUTHENTICATED, a, "B");
        String c = PolarisHelpers.newFolder(AUTHENTICATED, b, "C");
        PolarisHelpers.newFile(AUTHENTICATED, c, "f1");

        checkTreeState(root, "tree/shouldProperlyCreateObjectTree.json");
    }

    @Test
    public void shouldNotAllowObjectToBeInsertedMultipleTimesIntoDifferentTrees() throws Exception {
        // construct root -> folder_1 -> filename
        String root = Identifiers.newRandomSharedFolder();
        String folder = PolarisHelpers.newFolder(AUTHENTICATED, root, "folder_1");
        String file = PolarisHelpers.newFile(AUTHENTICATED, folder, "filename");

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // attempt to reinsert filename into root to create:
        // root -> (folder_1 -> filename, filename)
        PolarisHelpers
                .newObject(AUTHENTICATED, root, file, "file2", ObjectType.FILE)
                .and()
                .assertThat().statusCode(SC_CONFLICT);

        // the tree wasn't changed, and no notifications were made
        checkTreeState(root, "tree/shouldNotAllowObjectToBeInsertedMultipleTimesIntoDifferentTrees.json");
        verify(polaris.getNotifier(), times(0)).publishUpdate(anyString());
    }

    @Test
    public void shouldAllowObjectToBeInsertedMultipleTimesIntoDifferentTreesAsPartOfMove() throws Exception {
        // construct root -> folder_1 -> filename
        String root = Identifiers.newRandomSharedFolder();
        String folder = PolarisHelpers.newFolder(AUTHENTICATED, root, "folder_1");
        String file = PolarisHelpers.newFile(AUTHENTICATED, folder, "filename");

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // move filename from folder_1 to root to create:
        // root -> (folder_1, filename)
        PolarisHelpers
                .moveObject(AUTHENTICATED, folder, root, file, "filename")
                .and()
                .assertThat().statusCode(SC_OK)
                .and()
                .body("updated[0].object.version", equalTo(2), "updated[1].object.version", equalTo(2)); // two objects are updated

        // the tree was changed, and that we got a single notification (the change was within the same root)
        checkTreeState(root, "tree/shouldAllowObjectToBeInsertedMultipleTimesIntoDifferentTreesAsPartOfMove.json");
        verify(polaris.getNotifier(), times(1)).publishUpdate(root);
    }

    @Test
    public void shouldTreatMoveOfObjectIntoSameParentButWithADifferentNameAsARename() throws Exception {
        // construct root -> folder_1 -> filename_original
        String root = Identifiers.newRandomSharedFolder();
        String folder = PolarisHelpers.newFolder(AUTHENTICATED, root, "folder_1");
        String file = PolarisHelpers.newFile(AUTHENTICATED, folder, "filename_original");

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // rename filename_original -> filename_modified
        // root -> folder_1 -> filename_modified
        PolarisHelpers
                .moveObject(AUTHENTICATED, folder, folder, file, "filename_modified")
                .and()
                .assertThat().statusCode(SC_OK)
                .and()
                .body("updated[0].object.oid", equalTo(folder), "updated[0].object.version", equalTo(2));

        // the tree was changed, and we get a single notification
        checkTreeState(root, "tree/shouldTreatMoveOfObjectIntoSameParentButWithADifferentNameAsARename.json");
        verify(polaris.getNotifier(), times(1)).publishUpdate(root);
    }

    @Test
    public void shouldNotAllowRenameIfItWouldCauseANameConflict() throws Exception {
        // construct root -> folder_1 -> filename_original
        String root = Identifiers.newRandomSharedFolder();
        String file = PolarisHelpers.newFile(AUTHENTICATED, root, "name2");
        PolarisHelpers.newFolder(AUTHENTICATED, root, "name1");

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // rename name2 -> name1
        // should fail, because name1 already exists
        PolarisHelpers
                .moveObject(AUTHENTICATED, root, root, file, "name1")
                .and()
                .assertThat().statusCode(Response.Status.CONFLICT.getStatusCode());

        // no changes should be made, and, since it was a conflict, no notification
        checkTreeState(root, "tree/shouldNotAllowRenameIfItWouldCauseANameConflict.json");
        verify(polaris.getNotifier(), times(0)).publishUpdate(anyString());
    }

    @Test
    public void shouldReturnANameConflictIfDifferentFilesWithSameNameAttemptedToBeInsertedIntoSameParent() throws Exception {
        // construct root -> filename
        String root = Identifiers.newRandomSharedFolder();
        PolarisHelpers.newFile(AUTHENTICATED, root, "filename");

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // attempt to create a new filename with the same name in root
        PolarisHelpers
                .newObject(AUTHENTICATED, root, Identifiers.newRandomObject(), "filename", ObjectType.FILE)
                .and()
                .assertThat().statusCode(Response.Status.CONFLICT.getStatusCode())
                .and()
                .body("error_code", equalTo(PolarisError.NAME_CONFLICT.code()));

        // no changes should be made, and, since it was a conflict, no notification
        checkTreeState(root, "tree/shouldReturnANameConflictIfDifferentFilesWithSameNameAttemptedToBeInsertedIntoSameParent.json");
        verify(polaris.getNotifier(), times(0)).publishUpdate(anyString());
    }

    @Test
    public void shouldAllowUpdateToADeletedObject() throws Exception {
        // construct root -> filename
        String root = Identifiers.newRandomSharedFolder();
        String file = PolarisHelpers.newFile(AUTHENTICATED, root, "filename");

        // update the content for the file
        PolarisHelpers
                .newContent(AUTHENTICATED, file, 0, "HASH", 100, 1024)
                .and()
                .assertThat().statusCode(SC_OK); // why is version 0?

        // now, delete the file
        PolarisHelpers
                .removeObject(AUTHENTICATED, root, file)
                .and()
                .assertThat().statusCode(SC_OK);

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // verify that I can still update it
        PolarisHelpers
                .newContent(AUTHENTICATED, file, 1, "HASH-2", 102, 1025)
                .and()
                .assertThat().statusCode(SC_OK);

        checkTreeState(Constants.NO_ROOT, "tree/shouldAllowUpdateToADeletedObject_0000.json");
        verify(polaris.getNotifier(), times(1)).publishUpdate(root); // <--- FIXME (AG): oh dear...this is bad, no?
    }

    @Test
    public void shouldAllowMoveFromDeletedParentToNonDeletedParent() throws Exception {
        // construct root -> folder_1 -> folder_1_1 -> file
        //           root -> folder_2
        String root = Identifiers.newRandomSharedFolder();
        String folder1 = PolarisHelpers.newFolder(AUTHENTICATED, root, "folder_1");
        String folder11 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder_1_1");
        String file = PolarisHelpers.newFile(AUTHENTICATED, folder11, "file");
        PolarisHelpers.newFolder(AUTHENTICATED, root, "folder_2");

        // now, delete folder_1
        PolarisHelpers
                .removeObject(AUTHENTICATED, root, folder1)
                .and()
                .assertThat().statusCode(SC_OK);

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // check that I can move the file
        PolarisHelpers
                .moveObject(AUTHENTICATED, folder11, root, file, "file")
                .and()
                .assertThat().statusCode(SC_OK);

        // and even its containing folder
        PolarisHelpers
                .moveObject(AUTHENTICATED, folder1, root, folder11, "folder_1_1")
                .and()
                .assertThat().statusCode(SC_OK);

        checkTreeState(root, "tree/shouldAllowMoveFromDeletedParentToNonDeletedParent.json");
        checkTreeState(Constants.NO_ROOT, "tree/shouldAllowMoveFromDeletedParentToNonDeletedParent_0000.json");
        verify(polaris.getNotifier(), times(2)).publishUpdate(root);
    }

    @Test
    public void shouldFailToModifySharedFolderToWhichUserDoesNotHavePermissions0() throws Exception {
        String root = Identifiers.newRandomSharedFolder();

        // throw an exception if this user attempts to access this shared folder
        doThrow(new AccessException(USERID, root, Access.READ, Access.WRITE)).when(polaris.getAccessManager()).checkAccess(eq(USERID), eq(root), anyVararg());

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // attempt to insert an object directly into the shared folder
        String folder = Identifiers.newRandomObject();
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new InsertChild(folder, ObjectType.FOLDER, "A"))
                .and()
                .when().post(PolarisTestServer.getObjectURL(root))
                .then().assertThat().statusCode(HttpStatus.SC_FORBIDDEN);

        // shouldn't get any updates
        verify(polaris.getNotifier(), times(0)).publishUpdate(anyString());
    }

    @Test
    public void shouldFailToModifySharedFolderToWhichUserDoesNotHavePermissions() throws Exception {
        String root = Identifiers.newRandomSharedFolder();

        // first, insert an object into the shared folder
        // this should succeed, because access manager allows everyone
        String folder0 = PolarisHelpers.newFolder(AUTHENTICATED, root, "folder0");

        // now, change the access manager to throw
        // if this user attempts to access the same shared folder
        doThrow(new AccessException(USERID, root, Access.READ, Access.WRITE)).when(polaris.getAccessManager()).checkAccess(eq(USERID), eq(root), anyVararg());

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // try insert a new folder as a child of folder0
        // this should fail, because folder1 is a child of root
        String folder1 = Identifiers.newRandomObject();
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new InsertChild(folder1, ObjectType.FOLDER, "folder1"))
                .and()
                .when().post(PolarisTestServer.getObjectURL(folder0))
                .then().assertThat().statusCode(HttpStatus.SC_FORBIDDEN);

        // shouldn't get any updates
        verify(polaris.getNotifier(), times(0)).publishUpdate(anyString());
    }

    @Test
    public void shouldTreatDifferentObjectResourceRequestsIndependentlyEvenIfOneFails() throws Exception {
        // setup the access manager to throw an exception
        // if we attempt to access the denied shared folder
        String root0 = Identifiers.newRandomSharedFolder();
        doThrow(new AccessException(USERID, root0, Access.READ, Access.WRITE)).when(polaris.getAccessManager()).checkAccess(eq(USERID), eq(root0), anyVararg());

        // now, make a call to insert the folder directly into the denied shared folder
        String folder = Identifiers.newRandomObject();
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new InsertChild(folder, ObjectType.FOLDER, "A"))
                .and()
                .when().post(PolarisTestServer.getObjectURL(root0))
                .then().assertThat().statusCode(HttpStatus.SC_FORBIDDEN);

        // we should be able to access other shared folders though...
        String root1 = Identifiers.newRandomSharedFolder();
        PolarisHelpers.newFolder(AUTHENTICATED, root1, "SHOULD_SUCCEED"); // ignore object created

        // should get only one update for root1
        verify(polaris.getNotifier(), times(1)).publishUpdate(root1);
    }

    private void checkTreeState(String root, String json) throws IOException {
        JsonNode actual = getActualTree(root);
        JsonNode wanted = getWantedTree(root, json);

        assertThat(actual, equalTo(wanted));
    }

    private static JsonNode getActualTree(String root) throws IOException {
        ObjectMapper mapper = PolarisHelpers.newCamelCaseMapper();
        return mapper.readTree(PolarisHelpers.getTreeAsStream(root));
    }

    private static JsonNode getWantedTree(String root, String resourcePath) throws IOException {
        ObjectMapper mapper = PolarisHelpers.newCamelCaseMapper();
        return mapper.createObjectNode().set(root, mapper.readTree(Resources.getResource(resourcePath)));
    }
}
