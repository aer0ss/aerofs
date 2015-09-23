package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.polaris.Constants;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.PolarisTestServer;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.PolarisUtilities;
import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.operation.MoveChild;
import com.aerofs.polaris.api.operation.OperationResult;
import com.aerofs.polaris.api.operation.Restore;
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
import org.mockito.ArgumentMatcher;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collection;
import java.util.Random;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.jayway.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.*;
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
        verify(polaris.getNotifier(), times(0)).notifyStoreUpdated(any(SID.class), any(Long.class));
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
        verify(polaris.getNotifier(), times(1)).notifyStoreUpdated(eq(store), any(Long.class));
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
        verify(polaris.getNotifier(), times(1)).notifyStoreUpdated(eq(store), any(Long.class));
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
        verify(polaris.getNotifier(), times(0)).notifyStoreUpdated(any(SID.class), any(Long.class));
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
        verify(polaris.getNotifier(), times(0)).notifyStoreUpdated(any(SID.class), any(Long.class));
    }

    @Test
    public void canRestoreDeletedObject() throws Exception {
        SID store = SID.generate();
        OID deleted_file = PolarisHelpers.newFile(AUTHENTICATED, store, "file");
        PolarisHelpers.removeFileOrFolder(AUTHENTICATED, store, deleted_file);

        reset(polaris.getNotifier());

        given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(new Restore())
                .and()
                .when().post(PolarisTestServer.getObjectURL(deleted_file))
                .then().assertThat().statusCode(SC_OK);

        checkTreeState(store, "tree/shouldAllowReinsertionOfDeletedObject.json");
        verify(polaris.getNotifier(), times(1)).notifyStoreUpdated(eq(store), any(Long.class));
    }

    @Test
    public void cannotRestoreNonDeletedObject() throws Exception {
        SID store = SID.generate();
        OID file = PolarisHelpers.newFile(AUTHENTICATED, store, "file");

        // can't restore a file that's not been deleted
        PolarisHelpers.restoreObject(AUTHENTICATED, file)
                .assertThat().statusCode(SC_BAD_REQUEST);

        // can't restore a nonexistent object
        PolarisHelpers.restoreObject(AUTHENTICATED, OID.generate())
                .assertThat().statusCode(SC_NOT_FOUND);
    }

    @Test
    public void deletedObjectsShouldNotCauseNameConflicts() throws Exception {
        SID store = SID.generate();
        OID deleted_file = PolarisHelpers.newFile(AUTHENTICATED, store, "file");

        // should cause name conflict
        PolarisHelpers.newObject(AUTHENTICATED, store, OID.generate(), "file", ObjectType.FILE).assertThat().statusCode(SC_CONFLICT);

        PolarisHelpers.removeFileOrFolder(AUTHENTICATED, store, deleted_file);

        // no more name conflict
        PolarisHelpers.newFile(AUTHENTICATED, store, "file");
    }

    @Test
    public void shouldAllowMoveFromDeletedParentToNonDeletedParent() throws Exception {
        // construct store -> folder_1 -> folder_1_1 -> file
        //           store -> folder_2
        SID store = SID.generate();
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, store, "folder_1");
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
        verify(polaris.getNotifier(), times(2)).notifyStoreUpdated(eq(store), any(Long.class));
    }

    @Test
    public void shouldFailToModifySharedFolderToWhichUserDoesNotHavePermissions0() throws Exception {
        SID store = SID.generate();

        // throw an exception if this user attempts to access this shared folder
        doThrow(new AccessException(USERID, store, Access.READ, Access.WRITE)).when(polaris.getAccessManager()).checkAccess(eq(USERID), argThat(new ContainingUniqueID(store)), anyVararg());

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
        verify(polaris.getNotifier(), times(0)).notifyStoreUpdated(any(SID.class), any(Long.class));
    }

    @Test
    public void shouldFailToModifySharedFolderToWhichUserDoesNotHavePermissions() throws Exception {
        SID store = SID.generate();

        // first, insert an object into the shared folder
        // this should succeed, because access manager allows everyone
        OID folder0 = PolarisHelpers.newFolder(AUTHENTICATED, store, "folder0");

        // now, change the access manager to throw
        // if this user attempts to access the same shared folder
        doThrow(new AccessException(USERID, store, Access.READ, Access.WRITE)).when(polaris.getAccessManager()).checkAccess(eq(USERID), argThat(new ContainingUniqueID(store)), anyVararg());

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
        verify(polaris.getNotifier(), times(0)).notifyStoreUpdated(any(SID.class), any(Long.class));
    }

    @Test
    public void shouldTreatDifferentObjectResourceRequestsIndependentlyEvenIfOneFails() throws Exception {
        // setup the access manager to throw an exception
        // if we attempt to access the denied shared folder
        SID store0 = SID.generate();
        doThrow(new AccessException(USERID, store0, Access.READ, Access.WRITE)).when(polaris.getAccessManager()).checkAccess(eq(USERID), argThat(new ContainingUniqueID(store0)), anyVararg());

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
        verify(polaris.getNotifier(), times(1)).notifyStoreUpdated(eq(store1), any(Long.class));
    }

    @Test
    public void shouldMigrateFolder() throws Exception {
        SID rootStore = SID.rootSID(USERID);
        OID sharedFolder = PolarisHelpers.newFolder(AUTHENTICATED, rootStore, "shared_folder");
        OID folder = PolarisHelpers.newFolder(AUTHENTICATED, rootStore, "folder");
        PolarisHelpers.newFile(AUTHENTICATED, folder, "nested_file");
        PolarisHelpers.newFile(AUTHENTICATED, rootStore, "file");
        PolarisHelpers.newFile(AUTHENTICATED, sharedFolder, "shared_file");
        PolarisHelpers.removeObject(AUTHENTICATED, sharedFolder, PolarisHelpers.newFile(AUTHENTICATED, sharedFolder, "deleted_file"));
        PolarisHelpers.newFolder(AUTHENTICATED, sharedFolder, "shared_folder_2");

        reset(polaris.getNotifier());

        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, sharedFolder).jobID, 5);

        SID sfSID = SID.folderOID2convertedStoreSID(sharedFolder);
        PolarisHelpers.newFile(AUTHENTICATED, sfSID, "new_file");

        checkTreeState(rootStore, "tree/shouldProperlyMigrateStore1.json");
        checkTreeState(sfSID, "tree/shouldProperlyMigrateStore2.json");

        // one notification from the share op
        verify(polaris.getNotifier(), times(1)).notifyStoreUpdated(eq(rootStore), any(Long.class));
        // one notification from the new file, and one from the end of migration
        verify(polaris.getNotifier(), times(2)).notifyStoreUpdated(eq(sfSID), any(Long.class));
    }

    @Test
    public void shouldFailToInsertMountUnderSharedFolder() throws Exception {
        SID store = SID.generate();
        PolarisHelpers.newObject(AUTHENTICATED, store, SID.generate(), "nested_sf", ObjectType.STORE)
                .assertThat().statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void shouldFailToShareFolderContainingSharedFolders() throws Exception {
        SID rootStore = SID.rootSID(USERID);
        OID mountPointDirectChild = PolarisHelpers.newFolder(AUTHENTICATED, rootStore, "folder1");
        OID nestedMountPoint = PolarisHelpers.newFolder(AUTHENTICATED, rootStore, "folder2");
        OID firstMountPoint = PolarisHelpers.newFolder(AUTHENTICATED, mountPointDirectChild, "shared_folder1");
        OID intermediateFolder = PolarisHelpers.newFolder(AUTHENTICATED, nestedMountPoint, "intermediate_folder");
        OID secondMountPoint = PolarisHelpers.newFolder(AUTHENTICATED, intermediateFolder, "shared_folder2");

        PolarisHelpers.shareFolder(AUTHENTICATED, firstMountPoint);
        PolarisHelpers.shareFolder(AUTHENTICATED, secondMountPoint);

        PolarisHelpers.shareObject(AUTHENTICATED, mountPointDirectChild)
                .assertThat().statusCode(HttpStatus.SC_BAD_REQUEST);

        PolarisHelpers.shareObject(AUTHENTICATED, nestedMountPoint)
                .assertThat().statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void shouldReturnPreviousTransformsForNoops() throws Exception {
        SID store = SID.generate();
        OID folder = OID.generate();
        OID file = OID.generate();
        OID renamedFile = OID.generate();
        byte[] hash1 = new byte[32], hash2 = new byte[32];
        Random random = new Random();
        random.nextBytes(hash1);
        random.nextBytes(hash2);
        PolarisHelpers.newObject(AUTHENTICATED, store, folder, "folder", ObjectType.FOLDER)
                .assertThat().body("updated[0].transform_timestamp", equalTo(1));

        PolarisHelpers.newObject(AUTHENTICATED, folder, file, "file", ObjectType.FILE)
                .assertThat().body("updated[0].transform_timestamp", equalTo(2));

        PolarisHelpers.newObject(AUTHENTICATED, folder, renamedFile, "other_file", ObjectType.FILE)
                .assertThat().body("updated[0].transform_timestamp", equalTo(3));

        PolarisHelpers.moveObject(AUTHENTICATED, folder, store, file, "moved_file")
                .assertThat().body("updated[0].transform_timestamp", equalTo(4), "updated[1].transform_timestamp", equalTo(5));

        PolarisHelpers.moveObject(AUTHENTICATED, folder, folder, renamedFile, "renamed_file")
                .assertThat().body("updated[0].transform_timestamp", equalTo(6));

        PolarisHelpers.newContent(AUTHENTICATED, file, Constants.INITIAL_OBJECT_VERSION, hash1, 100, 1024)
                .assertThat().body("updated[0].transform_timestamp", equalTo(7));

        // any operations that would do no work should return the same timestamps as their original
        PolarisHelpers.newObject(AUTHENTICATED, store, folder, "folder", ObjectType.FOLDER)
                .assertThat().body("updated[0].transform_timestamp", equalTo(1));

        PolarisHelpers.moveObject(AUTHENTICATED, folder, store, file, "moved_file")
                .assertThat().body("updated[0].transform_timestamp", equalTo(4), "updated[1].transform_timestamp", equalTo(5));

        PolarisHelpers.moveObject(AUTHENTICATED, folder, folder, renamedFile, "renamed_file")
                .assertThat().body("updated[0].transform_timestamp", equalTo(6));

        PolarisHelpers.newContent(AUTHENTICATED, file, Constants.INITIAL_OBJECT_VERSION, hash1, 100, 1024)
                .assertThat().body("updated[0].transform_timestamp", equalTo(7));

        // removes make some of the other operations no longer no-ops, so we test these at the end

        PolarisHelpers.removeObject(AUTHENTICATED, folder, renamedFile)
                .assertThat().body("updated[0].transform_timestamp", equalTo(8));

        PolarisHelpers.removeObject(AUTHENTICATED, folder, renamedFile)
                .assertThat().body("updated[0].transform_timestamp", equalTo(8));

        // transform 9
        OID sharedFolder = PolarisHelpers.newFolder(AUTHENTICATED, SID.rootSID(USERID), "sharedfolder");
        PolarisHelpers.newObject(AUTHENTICATED, SID.rootSID(USERID), sharedFolder, "sharedfolder", ObjectType.FOLDER)
                .assertThat().body("updated[0].transform_timestamp", equalTo(9));

        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, sharedFolder).jobID, 5);
        PolarisHelpers.newObject(AUTHENTICATED, SID.rootSID(USERID), SID.folderOID2convertedAnchorOID(sharedFolder), "sharedfolder", ObjectType.STORE)
                .assertThat().body("updated[0].transform_timestamp", equalTo(10));
    }

    @Test
    public void shouldReturn409OnMountPointReinsertion()
    {
        SID rootStore = SID.rootSID(USERID);
        OID folder = PolarisHelpers.newFolder(AUTHENTICATED, rootStore, "folder1");
        OID sharedFolder = PolarisHelpers.newFolder(AUTHENTICATED, folder, "shared_folder1");

        PolarisHelpers.shareFolder(AUTHENTICATED, sharedFolder);

        PolarisHelpers.newObject(AUTHENTICATED, rootStore, SID.folderOID2convertedStoreSID(sharedFolder), "shared_folder1", ObjectType.STORE)
                .assertThat().statusCode(HttpStatus.SC_CONFLICT);

        // similar behavior if reinserting the shared folder with a different name
        PolarisHelpers.newObject(AUTHENTICATED, folder, SID.folderOID2convertedStoreSID(sharedFolder), "shared_folder2", ObjectType.STORE)
                .assertThat().statusCode(HttpStatus.SC_CONFLICT);
    }

    @Test
    public void shouldNotAllowModificationsToMountpointAfterSharing()
            throws Exception
    {
        SID rootStore = SID.rootSID(USERID);
        OID sharedFolder = PolarisHelpers.newFolder(AUTHENTICATED, rootStore, "shared_folder1");
        OID sharedFile = PolarisHelpers.newFile(AUTHENTICATED, sharedFolder, "shared_file");

        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, sharedFolder).jobID, 5);

        PolarisHelpers.newObject(AUTHENTICATED, sharedFolder, OID.generate(), "under_mountpoint", ObjectType.FILE)
                .assertThat().statusCode(HttpStatus.SC_CONFLICT);

        PolarisHelpers.removeObject(AUTHENTICATED, sharedFolder, sharedFile)
                .assertThat().statusCode(HttpStatus.SC_CONFLICT);

        OID file = PolarisHelpers.newFile(AUTHENTICATED, rootStore, "file");
        PolarisHelpers.moveObject(AUTHENTICATED, rootStore, sharedFolder, file, "file")
                .assertThat().statusCode(HttpStatus.SC_CONFLICT);

        PolarisHelpers.moveObject(AUTHENTICATED, sharedFolder, rootStore, sharedFile, "shared_file")
                .assertThat().statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void shouldMoveObjectsBetweenStores()
            throws Exception
    {
        byte[] hash = PolarisUtilities.hexDecode("95A8CD21628626307EEDD4439F0E40E3E5293AFD16305D8A4E82D9F851AE7AAF");
        SID share1 = SID.generate(), share2 = SID.generate();
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, share1, "src_folder");
        for (int i = 0; i < 4; i++) {
            OID file = PolarisHelpers.newFile(AUTHENTICATED, folder1, String.format("file%d", i));
            PolarisHelpers.newFileContent(AUTHENTICATED, file, 0, hash, 100, 1024);
            PolarisHelpers.newFolder(AUTHENTICATED, folder1, String.format("folder%d", i));
        }

        OID file = PolarisHelpers.newFile(AUTHENTICATED, share1, "src_file");
        reset(polaris.getNotifier());
        PolarisHelpers.moveObject(AUTHENTICATED, share1, share2, file, "dest_file")
                .assertThat().statusCode(HttpStatus.SC_OK);
        verify(polaris.getNotifier(), times(1)).notifyStoreUpdated(eq(share1), any(Long.class));
        verify(polaris.getNotifier(), times(1)).notifyStoreUpdated(eq(share2), any(Long.class));

        reset(polaris.getNotifier());
        OperationResult result =  PolarisHelpers.moveObject(AUTHENTICATED, share1, share2, folder1, "dest_folder")
                .assertThat().statusCode(HttpStatus.SC_OK)
                .and().extract().response().as(OperationResult.class);
        // cross-store moves of folders cause an actual migration
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, result.jobID, 10);

        verify(polaris.getNotifier(), times(1)).notifyStoreUpdated(eq(share1), any(Long.class));
        // one notification for creating the migration destination, then another for finishing the migration
        verify(polaris.getNotifier(), times(2)).notifyStoreUpdated(eq(share2), any(Long.class));
        checkTreeState(share1, "tree/shouldMigrateCrossStore1.json");
        checkTreeState(share2, "tree/shouldMigrateCrossStore2.json");
    }

    @Test
    public void shouldThrowParentConflictAcrossStores()
            throws Exception
    {
        SID share1 = SID.generate(), share2 = SID.generate();
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, share1, "folder1");
        PolarisHelpers.newFolder(AUTHENTICATED, share2, "folder2");

        PolarisHelpers.moveObject(AUTHENTICATED, share1, share2, folder1, "folder2")
                .assertThat().statusCode(HttpStatus.SC_CONFLICT);
    }

    @Test
    public void shouldBeAbleToMoveAnchors()
            throws Exception
    {
        SID rootStore = SID.rootSID(USERID);
        OID folder = PolarisHelpers.newFolder(AUTHENTICATED, rootStore, "folder");
        OID sf = PolarisHelpers.newFolder(AUTHENTICATED, folder, "shared_folder");
        OID anchor = SID.folderOID2convertedAnchorOID(sf);

        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, sf).jobID, 5);

        PolarisHelpers.moveFileOrFolder(AUTHENTICATED, folder, rootStore, anchor, "new_name");
        PolarisHelpers.moveFileOrFolder(AUTHENTICATED, rootStore, folder, anchor, "old_name");
    }

    @Test
    public void shouldDisallowCyclicalMove()
            throws Exception
    {
        SID share = SID.generate();
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, share, "folder1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder2");

        PolarisHelpers.moveObject(AUTHENTICATED, share, folder2, folder1, "folder1")
                .assertThat().statusCode(SC_BAD_REQUEST);
    }

    @Test
    public void shouldDisallowCyclicalMoveAcrossStores()
            throws Exception
    {
        SID rootStore = SID.rootSID(USERID);
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootStore, "folder1");
        OID sf = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "shared_folder");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, sf, "folder2");

        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, sf).jobID, 5);

        PolarisHelpers.moveObject(AUTHENTICATED, rootStore, folder2, folder1, "folder1")
                .assertThat().statusCode(SC_BAD_REQUEST);
    }

    @Test
    public void shouldAllowInsertionOfStoreRootWithoutShare()
            throws Exception
    {
        // this happens when another user creates an external root and invites users to it
        SID rootStore = SID.rootSID(USERID);
        PolarisHelpers.newObject(AUTHENTICATED, rootStore, SID.generate(), "sharedfolder", ObjectType.STORE)
                .assertThat().statusCode(SC_OK);
    }

    @Test
    public void shouldNotAllowOperationsOnDeletedObject()
            throws Exception
    {
        // it is not permitted to rename, or move an explicitly deleted object
        SID store = SID.generate();
        OID otherparent = PolarisHelpers.newFolder(AUTHENTICATED, store, "parent");
        OID deletedFile = PolarisHelpers.newFile(AUTHENTICATED, store, "file");
        OID deletedFolder = PolarisHelpers.newFolder(AUTHENTICATED, store, "folder");
        PolarisHelpers.removeFileOrFolder(AUTHENTICATED, store, deletedFile);
        PolarisHelpers.removeFileOrFolder(AUTHENTICATED, store, deletedFolder);

        PolarisHelpers.moveObject(AUTHENTICATED, store, store, deletedFile, "new name")
                .assertThat().statusCode(SC_BAD_REQUEST);
        PolarisHelpers.moveObject(AUTHENTICATED, store, otherparent, deletedFile, "file")
                .assertThat().statusCode(SC_BAD_REQUEST);

        PolarisHelpers.newContent(AUTHENTICATED, deletedFile, 0, PolarisUtilities.hexDecode("95A8CD21628626307EEDD4439F0E40E3E5293AFD16305D8A4E82D9F851AE7AAF"), 1024, 100)
                .assertThat().statusCode(SC_BAD_REQUEST);

        PolarisHelpers.shareObject(AUTHENTICATED, deletedFolder)
                .assertThat().statusCode(SC_BAD_REQUEST);
    }

    @Test
    public void shouldAllowOperationsOnImplicitlyDeletedObjects()
            throws Exception
    {
        SID store = SID.generate();
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, store, "folder_1");
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, folder1, "file1");
        OID file2 = PolarisHelpers.newFile(AUTHENTICATED, folder1, "file2");

        PolarisHelpers.removeFileOrFolder(AUTHENTICATED, store, folder1);

        reset(polaris.getNotifier());

        PolarisHelpers.moveFileOrFolder(AUTHENTICATED, folder1, folder1, file1, "new name");
        PolarisHelpers.removeFileOrFolder(AUTHENTICATED, folder1, file1);

        PolarisHelpers.newFileContent(AUTHENTICATED, file2, 0, PolarisUtilities.hexDecode("95A8CD21628626307EEDD4439F0E40E3E5293AFD16305D8A4E82D9F851AE7AAF"), 1024, 100);

        // as should updates
        verify(polaris.getNotifier(), times(3)).notifyStoreUpdated(eq(store), any(Long.class));
    }

    @Test
    public void shouldBeAbleToReinsertAnchor()
            throws Exception
    {
        SID rootStore = SID.rootSID(USERID);
        SID store = SID.generate();
        PolarisHelpers.newObject(AUTHENTICATED, rootStore, store, "sharedfolder", ObjectType.STORE)
                .assertThat().statusCode(SC_OK);
        PolarisHelpers.removeFileOrFolder(AUTHENTICATED, rootStore, SID.storeSID2anchorOID(store));

        // reinserting it, as if upon rejoining the store
        PolarisHelpers.newObject(AUTHENTICATED, rootStore, store, "sharedfolder", ObjectType.STORE)
                .assertThat().statusCode(SC_OK);
    }

    @Test
    public void shouldRequirePermissionsOnBothStoresForCrossStoreMove()
            throws Exception
    {
        SID store1 = SID.generate();
        SID store2 = SID.generate();

        // first, insert an object into the shared folder
        // this should succeed, because access manager allows everyone
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, store1, "folder1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, store2, "folder2");

        // now, change the access manager to throw
        // if this user attempts to access the same shared folder
        doThrow(new AccessException(USERID, store2, Access.READ, Access.WRITE)).when(polaris.getAccessManager()).checkAccess(eq(USERID), argThat(new ContainingUniqueID(store2)), anyVararg());

        // remove any notifications as a result of the setup actions
        reset(polaris.getNotifier());

        // try to move folder1 from store1 to store2, which should fail because the user does not have access to store2
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .body(new MoveChild(folder1, store2, "folder1"))
                .and()
                .when().post(PolarisTestServer.getObjectURL(store1))
                .then().assertThat().statusCode(HttpStatus.SC_FORBIDDEN);

        // same thing in reverse
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .body(new MoveChild(folder2, store1, "folder2"))
                .and()
                .when().post(PolarisTestServer.getObjectURL(store2))
                .then().assertThat().statusCode(HttpStatus.SC_FORBIDDEN);

        // and definitely so if both stores are restricted
        doThrow(new AccessException(USERID, store1, Access.READ, Access.WRITE)).when(polaris.getAccessManager()).checkAccess(eq(USERID), argThat(new ContainingUniqueID(store1)), anyVararg());

        // try to move folder1 from store1 to store2, which should fail because the user does not have access to store2
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .body(new MoveChild(folder1, store2, "folder1"))
                .and()
                .when().post(PolarisTestServer.getObjectURL(store1))
                .then().assertThat().statusCode(HttpStatus.SC_FORBIDDEN);

        // same thing in reverse
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .body(new MoveChild(folder2, store1, "folder2"))
                .and()
                .when().post(PolarisTestServer.getObjectURL(store2))
                .then().assertThat().statusCode(HttpStatus.SC_FORBIDDEN);

        // shouldn't get any updates
        verify(polaris.getNotifier(), times(0)).notifyStoreUpdated(any(SID.class), any(Long.class));
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

    private class ContainingUniqueID extends ArgumentMatcher<Collection<UniqueID>> {
        private UniqueID match;

        public ContainingUniqueID(UniqueID match) {
            this.match = match;
        }

        @SuppressWarnings("unchecked")
        public boolean matches(Object collection) {
            for (UniqueID id : (Collection<UniqueID>) collection) {
                if (match.equals(id)) return true;
            }
            return false;
        }
    }
}
