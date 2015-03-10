package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.PolarisTestServer;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.PolarisUtilities;
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
import java.util.Random;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.jayway.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.any;
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

    private static final UserID USERID = UserID.fromInternal("test@aerofs.com");
    private static final DID DEVICE = DID.generate();
    private static final RequestSpecification AUTHENTICATED = PolarisHelpers.newAuthedAeroUserReqSpec(USERID, DEVICE);

    private final MySQLDatabase database = new MySQLDatabase("test");
    private final PolarisTestServer polaris = new PolarisTestServer();

    @Rule
    public RuleChain chain = RuleChain.outerRule(database).around(polaris);

    @Test
    public void shouldProperlyCreateObjectTree() throws Exception {
        SID store = SID.generate();
        OID a = PolarisHelpers.newFolder(AUTHENTICATED, store, "A");
        OID b = PolarisHelpers.newFolder(AUTHENTICATED, a, "B");
        OID c = PolarisHelpers.newFolder(AUTHENTICATED, b, "C");
        PolarisHelpers.newFile(AUTHENTICATED, c, "f1");

        checkTreeState(store, "tree/shouldProperlyCreateObjectTree.json");
    }

    @Test
    public void shouldNotAllowObjectToBeInsertedMultipleTimesIntoDifferentTrees() throws Exception {
        // construct store -> folder_1 -> filename
        SID store = SID.generate();
        OID folder = PolarisHelpers.newFolder(AUTHENTICATED, store, "folder_1");
        OID file = PolarisHelpers.newFile(AUTHENTICATED, folder, "filename");

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // attempt to reinsert filename into store to create:
        // store -> (folder_1 -> filename, filename)
        PolarisHelpers
                .newObject(AUTHENTICATED, store, file, "file2", ObjectType.FILE)
                .and()
                .assertThat().statusCode(SC_CONFLICT);

        // the tree wasn't changed, and no notifications were made
        checkTreeState(store, "tree/shouldNotAllowObjectToBeInsertedMultipleTimesIntoDifferentTrees.json");
        verify(polaris.getNotifier(), times(0)).notifyStoreUpdated(any(SID.class));
    }

    @Test
    public void shouldAllowObjectToBeInsertedMultipleTimesIntoDifferentTreesAsPartOfMove() throws Exception {
        // construct store -> folder_1 -> filename
        SID store = SID.generate();
        OID folder = PolarisHelpers.newFolder(AUTHENTICATED, store, "folder_1");
        OID file = PolarisHelpers.newFile(AUTHENTICATED, folder, "filename");

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // move filename from folder_1 to store to create:
        // store -> (folder_1, filename)
        PolarisHelpers
                .moveObject(AUTHENTICATED, folder, store, file, "filename")
                .and()
                .assertThat().statusCode(SC_OK)
                .and()
                .body("updated[0].object.version", equalTo(2), "updated[1].object.version", equalTo(2)); // two objects are updated

        // the tree was changed, and that we got a single notification (the change was within the same store)
        checkTreeState(store, "tree/shouldAllowObjectToBeInsertedMultipleTimesIntoDifferentTreesAsPartOfMove.json");
        verify(polaris.getNotifier(), times(1)).notifyStoreUpdated(store);
    }

    @Test
    public void shouldTreatMoveOfObjectIntoSameParentButWithADifferentNameAsARename() throws Exception {
        // construct store -> folder_1 -> filename_original
        SID store = SID.generate();
        OID folder = PolarisHelpers.newFolder(AUTHENTICATED, store, "folder_1");
        OID file = PolarisHelpers.newFile(AUTHENTICATED, folder, "filename_original");

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // rename filename_original -> filename_modified
        // store -> folder_1 -> filename_modified
        PolarisHelpers
                .moveObject(AUTHENTICATED, folder, folder, file, "filename_modified")
                .and()
                .assertThat().statusCode(SC_OK)
                .and()
                .body("updated[0].object.oid", equalTo(folder.toStringFormal()), "updated[0].object.version", equalTo(2));

        // the tree was changed, and we get a single notification
        checkTreeState(store, "tree/shouldTreatMoveOfObjectIntoSameParentButWithADifferentNameAsARename.json");
        verify(polaris.getNotifier(), times(1)).notifyStoreUpdated(store);
    }

    @Test
    public void shouldNotAllowRenameIfItWouldCauseANameConflict() throws Exception {
        // construct store -> folder_1 -> filename_original
        SID store = SID.generate();
        OID file = PolarisHelpers.newFile(AUTHENTICATED, store, "name2");
        PolarisHelpers.newFolder(AUTHENTICATED, store, "name1");

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // rename name2 -> name1
        // should fail, because name1 already exists
        PolarisHelpers
                .moveObject(AUTHENTICATED, store, store, file, "name1")
                .and()
                .assertThat().statusCode(Response.Status.CONFLICT.getStatusCode());

        // no changes should be made, and, since it was a conflict, no notification
        checkTreeState(store, "tree/shouldNotAllowRenameIfItWouldCauseANameConflict.json");
        verify(polaris.getNotifier(), times(0)).notifyStoreUpdated(any(SID.class));
    }

    @Test
    public void shouldReturnANameConflictIfDifferentFilesWithSameNameAttemptedToBeInsertedIntoSameParent() throws Exception {
        // construct store -> filename
        SID store = SID.generate();
        PolarisHelpers.newFile(AUTHENTICATED, store, "filename");

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // attempt to create a new filename with the same name in store
        PolarisHelpers
                .newObject(AUTHENTICATED, store, OID.generate(), "filename", ObjectType.FILE)
                .and()
                .assertThat().statusCode(Response.Status.CONFLICT.getStatusCode())
                .and()
                .body("error_code", equalTo(PolarisError.NAME_CONFLICT.code()));

        // no changes should be made, and, since it was a conflict, no notification
        checkTreeState(store, "tree/shouldReturnANameConflictIfDifferentFilesWithSameNameAttemptedToBeInsertedIntoSameParent.json");
        verify(polaris.getNotifier(), times(0)).notifyStoreUpdated(any(SID.class));
    }

    @Test
    public void shouldAllowUpdateToADeletedObject() throws Exception {
        // construct store -> filename
        SID store = SID.generate();
        OID file = new OID(PolarisUtilities.hexDecode("F57205C5C8C0427EBEB571ED41294CC7"));
        PolarisHelpers.newFileUsingOID(AUTHENTICATED, store, file, "filename");
        byte[] hash = PolarisUtilities.hexDecode("95A8CD21628626307EEDD4439F0E40E3E5293AFD16305D8A4E82D9F851AE7AAF");

        // create a fake hash for the initial file
        byte[] initialHash = new byte[32];
        Random random = new Random();
        random.nextBytes(initialHash);

        // update the initial content for the file
        PolarisHelpers
                .newContent(AUTHENTICATED, file, 0, initialHash, 100, 1024)
                .and()
                .assertThat().statusCode(SC_OK); // why is version 0?

        // now, delete the file
        PolarisHelpers
                .removeObject(AUTHENTICATED, store, file)
                .and()
                .assertThat().statusCode(SC_OK);

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // verify that I can still update it
        PolarisHelpers
                .newContent(AUTHENTICATED, file, 1, hash, 102, 1025)
                .and()
                .assertThat().statusCode(SC_OK);

        checkTreeState(OID.TRASH, "tree/shouldAllowUpdateToADeletedObject_0000.json");
        verify(polaris.getNotifier(), times(1)).notifyStoreUpdated(store); // <--- FIXME (AG): oh dear...this is bad, no?
    }

    @Test
    public void shouldAllowMoveFromDeletedParentToNonDeletedParent() throws Exception {
        // construct store -> folder_1 -> folder_1_1 -> file
        //           store -> folder_2
        SID store = SID.generate();
        OID folder1 = new OID(PolarisUtilities.hexDecode("F57205C5C8C0427EBEB571ED41294CC7"));
        PolarisHelpers.newFolderUsingOID(AUTHENTICATED, store, folder1, "folder_1");
        OID folder11 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder_1_1");
        OID file = PolarisHelpers.newFile(AUTHENTICATED, folder11, "file");
        PolarisHelpers.newFolder(AUTHENTICATED, store, "folder_2");

        // now, delete folder_1
        PolarisHelpers
                .removeObject(AUTHENTICATED, store, folder1)
                .and()
                .assertThat().statusCode(SC_OK);

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // check that I can move the file
        PolarisHelpers
                .moveObject(AUTHENTICATED, folder11, store, file, "file")
                .and()
                .assertThat().statusCode(SC_OK);

        // and even its containing folder
        PolarisHelpers
                .moveObject(AUTHENTICATED, folder1, store, folder11, "folder_1_1")
                .and()
                .assertThat().statusCode(SC_OK);

        checkTreeState(store, "tree/shouldAllowMoveFromDeletedParentToNonDeletedParent.json");
        checkTreeState(OID.TRASH, "tree/shouldAllowMoveFromDeletedParentToNonDeletedParent_0000.json");
        verify(polaris.getNotifier(), times(2)).notifyStoreUpdated(store);
    }

    @Test
    public void shouldFailToModifySharedFolderToWhichUserDoesNotHavePermissions0() throws Exception {
        SID store = SID.generate();

        // throw an exception if this user attempts to access this shared folder
        doThrow(new AccessException(USERID, store, Access.READ, Access.WRITE)).when(polaris.getAccessManager()).checkAccess(eq(USERID), eq(store), anyVararg());

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // attempt to insert an object directly into the shared folder
        OID folder = OID.generate();
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new InsertChild(folder, ObjectType.FOLDER, "A"))
                .and()
                .when().post(PolarisTestServer.getObjectURL(store))
                .then().assertThat().statusCode(HttpStatus.SC_FORBIDDEN);

        // shouldn't get any updates
        verify(polaris.getNotifier(), times(0)).notifyStoreUpdated(any(SID.class));
    }

    @Test
    public void shouldFailToModifySharedFolderToWhichUserDoesNotHavePermissions() throws Exception {
        SID store = SID.generate();

        // first, insert an object into the shared folder
        // this should succeed, because access manager allows everyone
        OID folder0 = PolarisHelpers.newFolder(AUTHENTICATED, store, "folder0");

        // now, change the access manager to throw
        // if this user attempts to access the same shared folder
        doThrow(new AccessException(USERID, store, Access.READ, Access.WRITE)).when(polaris.getAccessManager()).checkAccess(eq(USERID), eq(store), anyVararg());

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // try insert a new folder as a child of folder0
        // this should fail, because folder1 is a child of store
        OID folder1 = OID.generate();
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new InsertChild(folder1, ObjectType.FOLDER, "folder1"))
                .and()
                .when().post(PolarisTestServer.getObjectURL(folder0))
                .then().assertThat().statusCode(HttpStatus.SC_FORBIDDEN);

        // shouldn't get any updates
        verify(polaris.getNotifier(), times(0)).notifyStoreUpdated(any(SID.class));
    }

    @Test
    public void shouldTreatDifferentObjectResourceRequestsIndependentlyEvenIfOneFails() throws Exception {
        // setup the access manager to throw an exception
        // if we attempt to access the denied shared folder
        SID store0 = SID.generate();
        doThrow(new AccessException(USERID, store0, Access.READ, Access.WRITE)).when(polaris.getAccessManager()).checkAccess(eq(USERID), eq(store0), anyVararg());

        // now, make a call to insert the folder directly into the denied shared folder
        OID folder = OID.generate();
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new InsertChild(folder, ObjectType.FOLDER, "A"))
                .and()
                .when().post(PolarisTestServer.getObjectURL(store0))
                .then().assertThat().statusCode(HttpStatus.SC_FORBIDDEN);

        // we should be able to access other shared folders though...
        SID store1 = SID.generate();
        PolarisHelpers.newFolder(AUTHENTICATED, store1, "SHOULD_SUCCEED"); // ignore object created

        // should get only one update for store1
        verify(polaris.getNotifier(), times(1)).notifyStoreUpdated(store1);
    }

    private void checkTreeState(UniqueID store, String json) throws IOException {
        JsonNode actual = getActualTree(store);
        JsonNode wanted = getWantedTree(store, json);

        assertThat(actual, equalTo(wanted));
    }

    private static JsonNode getActualTree(UniqueID store) throws IOException {
        ObjectMapper mapper = PolarisHelpers.newPolarisMapper();
        return mapper.readTree(PolarisHelpers.getTreeAsStream(store));
    }

    private static JsonNode getWantedTree(UniqueID store, String resourcePath) throws IOException {
        ObjectMapper mapper = PolarisHelpers.newPolarisMapper();
        return mapper.createObjectNode().set(store.toStringFormal(), mapper.readTree(Resources.getResource(resourcePath)));
    }
}
