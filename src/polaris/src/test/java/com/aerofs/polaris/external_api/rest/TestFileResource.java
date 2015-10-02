package com.aerofs.polaris.external_api.rest;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.id.RestObject;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.rest.api.CommonMetadata;
import com.jayway.restassured.http.ContentType;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import static com.aerofs.polaris.PolarisTestServer.getApiFilesURL;
import static org.hamcrest.Matchers.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.doThrow;

public class TestFileResource extends AbstractRestTest
{
    private byte[] initialHash;

    @Before
    public void setup()
    {
        initialHash = new byte[32];
        Random random = new Random();
        random.nextBytes(initialHash);
    }

    @Test
    public void shouldReturn400ForInvalidId() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when().get(getApiFilesURL() +  "bla");
    }

    @Test
    public void shouldReturn404ForNonExistingStore() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(getApiFilesURL() + new RestObject(SID.generate(), OID.generate())
                .toStringFormal());
    }

    @Test
    public void shouldReturn404ForNonExistingDir() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(getApiFilesURL() + new RestObject(rootSID, OID.generate())
                    .toStringFormal());
    }

    @Test
    public void shouldReturn404ForDir() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        RestObject objectFolder1 = new RestObject(rootSID, folder1);

        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(getApiFilesURL() + objectFolder1.toStringFormal());
    }

    @Test
    public void shouldGetMetadata() throws Exception
    {
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");
        RestObject restFile1 = new RestObject(rootSID, file1);

        PolarisHelpers.newFileContent(AUTHENTICATED, file1, 0, initialHash, 1, 100);
        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(restFile1.toStringFormal()))
                .body("name", equalTo("file1"))
                .body("size", equalTo(1))
                .body("last_modified", equalTo(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .format(new Date(100))))
                .body("mime_type", equalTo("application/octet-stream"))
                .body("etag", equalTo(BaseUtil.hexEncode(initialHash)))
                .body("content_state", equalTo(null))
        .when().get(getApiFilesURL() + restFile1.toStringFormal());
    }

    @Test
    public void shouldGetMetadataWithOnDemandFields() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, folder1, "file1");
        PolarisHelpers.newFileContent(AUTHENTICATED, file1, 0, initialHash, 1, 100);
        RestObject restFolder1 = new RestObject(rootSID, folder1);
        RestObject restFile1 = new RestObject(rootSID, file1);

        givenAccess()
                .queryParam("fields", "path,children")
        .expect()
                .statusCode(200)
                .body("id", equalTo(restFile1.toStringFormal()))
                .body("name", equalTo("file1"))
                .body("size", equalTo(1))
                .body("mime_type", equalTo("application/octet-stream"))
                .body("etag", equalTo(BaseUtil.hexEncode(initialHash)))
                .body("content_state", equalTo(null))
                .body("path.folders", iterableWithSize(2))
                .body("path.folders[0].name", equalTo("AeroFS"))
                .body("path.folders[0].id", equalTo(new RestObject(rootSID, OID.ROOT).toStringFormal()))
                .body("path.folders[0].is_shared", equalTo(false))
                .body("path.folders[1].name", equalTo("folder1"))
                .body("path.folders[1].id", equalTo(restFolder1.toStringFormal()))
                .body("path.folders[1].is_shared", equalTo(false))
        .when().log().everything()
                .get(getApiFilesURL() + restFile1.toStringFormal());
    }

    @Test
    public void shouldGetMetadataWithMIME() throws Exception
    {
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1.txt");
        RestObject restFile1 = new RestObject(rootSID, file1);

        PolarisHelpers.newFileContent(AUTHENTICATED, file1, 0, initialHash, 1, 100);

        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(restFile1.toStringFormal()))
                .body("name", equalTo("file1.txt"))
                .body("size", equalTo(1))
                .body("last_modified", equalTo(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                        .format(new Date(100))))
                .body("mime_type", equalTo("text/plain"))
                .body("etag", equalTo(BaseUtil.hexEncode(initialHash)))
                .body("content_state", nullValue())
        .get(getApiFilesURL() + restFile1.toStringFormal());
    }

    @Test
    public void shouldReturnPath() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID share1 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "share1");
        PolarisHelpers.shareFolder(AUTHENTICATED, share1);
        SID sid2 = SID.folderOID2convertedStoreSID(share1);
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, sid2, "file1");

        String root = new RestObject(rootSID, OID.ROOT).toStringFormal();
        String folder1Rest = new RestObject(rootSID, folder1).toStringFormal();
        String share1Rest = new RestObject(rootSID, SID.storeSID2anchorOID(sid2)).toStringFormal();
        String file1Rest = new RestObject(sid2, file1).toStringFormal();

        givenAccess()
        .expect()
                .statusCode(200)
                .body("folders[0].id", equalTo(root))
                .body("folders[0].name", equalTo("AeroFS"))
                .body("folders[0].is_shared", equalTo(false))

                .body("folders[1].id", equalTo(folder1Rest))
                .body("folders[1].name", equalTo("folder1"))
                .body("folders[1].is_shared", equalTo(false))
                .body("folders[1].parent", equalTo(root))

                .body("folders[2].id", equalTo(share1Rest))
                .body("folders[2].name", equalTo("share1"))
                .body("folders[2].is_shared", equalTo(true))
                .body("folders[2].parent", equalTo(folder1Rest))
        .when().log().everything()
                .get(getApiFilesURL() + file1Rest + "/path/");
    }

    @Test
    public void shouldReturn404ForInvalidPath() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().log().everything()
                .get(getApiFilesURL() + new RestObject(rootSID, OID.generate()).toStringFormal()
                        + "/path");
    }

    @Test
    public void shouldCreateFile() throws Exception
    {
        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(new RestObject(rootSID, OID.ROOT).toStringFormal(),
                        "foo.txt")))
        .expect()
                .statusCode(201)
                .body("name", equalTo("foo.txt"))
                .body("content_state", nullValue())
                .body("size", nullValue())
                .body("last_modified", nullValue())
        .when().post(getApiFilesURL());
    }

    @Test
    public void shouldCreateFileUnderRoot() throws Exception
    {

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child("root", "foo.txt")))
        .expect()
                .statusCode(201)
                .body("name", equalTo("foo.txt"))
                .body("content_state", nullValue())
                .body("size", nullValue())
                .body("last_modified", nullValue())
        .when().post(getApiFilesURL());
    }

    @Test
    public void shouldCreateFileUnderAnchor() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        PolarisHelpers.shareFolder(AUTHENTICATED, folder1);
        RestObject restShared1 = new RestObject(rootSID, SID.folderOID2convertedAnchorOID(folder1));

        givenAccess()
                .contentType(ContentType.JSON)
                .body(CommonMetadata.child(restShared1.toStringFormal(), "foo.txt"))
        .expect()
                .statusCode(201)
                .body("name", equalTo("foo.txt"))
                .body("content_state", nullValue())
                .body("size", nullValue())
                .body("last_modified", nullValue())
        .when().post(getApiFilesURL());
    }

    @Test
    public void shouldReturn409WhenAlreadyExists() throws Exception
    {

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child("root", "foo.txt")))
        .expect()
                .statusCode(201)
        .when().post(getApiFilesURL());

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child("root", "foo.txt")))
        .expect()
                .statusCode(409)
                .body("type", equalTo("CONFLICT"))
        .when().post(getApiFilesURL());
    }

    @Test
    public void shouldReturn400IfNoFileObjectGiven() throws Exception
    {
        givenAccess()
                .contentType(ContentType.JSON)
                .body("")
        .expect()
                .statusCode(400)
        .when().post(getApiFilesURL());
    }

    @Test
    public void shouldReturn404WhenCreatingUnderNonExistingParent() throws Exception {
        givenAccess()
                .contentType(ContentType.JSON)
                .body(CommonMetadata.child(new RestObject(rootSID, OID.generate()).toStringFormal(),
                        "foo"))
        .expect()
                .statusCode(404)
        .when().post(getApiFilesURL());
    }

    @Test
    public void shouldReturn400WhenCreatingUnderFile() throws Exception
    {
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");
        RestObject restObject = new RestObject(rootSID, file1);

        givenAccess()
                .contentType(ContentType.JSON)
                .body(CommonMetadata.child(restObject.toStringFormal(), "bar"))
        .expect()
                .statusCode(400)
        .when().post(getApiFilesURL());
    }

    @Test
    public void shouldReturn403WhenViewerTriesToCreate() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");

        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, folder1).jobID, 5);
        SID sid1 = SID.folderOID2convertedStoreSID(folder1);
        RestObject objectFolder1 = new RestObject(sid1, OID.ROOT);

        doThrow(new AccessException(USERID, sid1, Access.WRITE))
                .when(polaris.getAccessManager()).checkAccess(eq(USERID),
                    anyCollectionOf(UniqueID.class), anyVararg());

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(objectFolder1.toStringFormal(), "foo.txt")))
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when().post(getApiFilesURL());
    }

    @Test
    public void shouldReturn403WhenTriesToCreateWithReadOnlyToken() throws Exception
    {
        givenReadAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(new RestObject(rootSID, OID.ROOT).toStringFormal(),
                        "foo.txt")))
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when().post(getApiFilesURL());
    }

    @Test
    public void shouldMoveFile() throws Exception
    {
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder2");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, folder2).toStringFormal(), "moved")))
        .expect()
                .statusCode(200)
                .body("id", equalTo(new RestObject(rootSID, file1).toStringFormal()))
                .body("name", equalTo("moved"))
                .body("parent", equalTo(new RestObject(rootSID, folder2).toStringFormal()))
        .when().put(getApiFilesURL() + new RestObject(rootSID, file1).toStringFormal());
    }

    @Ignore("https://aerofs.atlassian.net/browse/ENG-3104")
    @Test
    public void shouldMoveFileUnderAnchor() throws Exception
    {
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder2");

        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, folder2).jobID, 5);
        SID sid2 = SID.folderOID2convertedStoreSID(folder2);

        System.out.println("Object to migrate: " + file1.toStringFormal());
        RestObject objectFolder2 = new RestObject(sid2, OID.ROOT);

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(sid2, OID.ROOT).toStringFormal(), "moved")))
                .expect()
                .statusCode(200)
                .body("name", equalTo("moved"))
                .body("parent", equalTo(objectFolder2.toStringFormal()))
                .when()
                .put(getApiFilesURL() + new RestObject(rootSID, file1).toStringFormal());
    }

    @Test
    public void shouldReturn409WhenMoveConflict() throws Exception
    {
        OID file1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "file1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder2");
        PolarisHelpers.newFile(AUTHENTICATED, folder2, "temp");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, folder2).toStringFormal(), "temp")))
        .expect()
                .statusCode(409)
                .body("type", equalTo(com.aerofs.rest.api.Error.Type.CONFLICT.toString()))
        .when().put(getApiFilesURL() + new RestObject(rootSID, file1).toStringFormal());
    }

    @Test
    public void shouldReturn404MovingNonExistingFile() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");

        givenAccess()
                .contentType(ContentType.JSON)
                .header(HttpHeaders.Names.IF_MATCH, OTHER_ETAG)
                .body(json(CommonMetadata.child(new RestObject(rootSID, folder1).toStringFormal(),
                        "bar")))
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().put(getApiFilesURL() + new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn404MovingToNonExistingParent() throws Exception
    {
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, OID.generate()).toStringFormal(), "test")))
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().put(getApiFilesURL() + new RestObject(rootSID, file1).toStringFormal());
    }

    @Test
    public void shouldReturn400MovingUnderFile() throws Exception
    {
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");
        OID file2 = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file2");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(new RestObject(rootSID, file1).toStringFormal(),
                        "bar")))
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put(getApiFilesURL() + new RestObject(rootSID, file2).toStringFormal());
    }

    @Test
    public void shouldReturn403WhenViewerTriesToMove() throws Exception {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, folder1, "file1");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, folder1).jobID, 5);

        SID sid1 = SID.folderOID2convertedStoreSID(folder1);
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder2");

        doThrow(new AccessException(USERID, sid1, Access.WRITE))
                .when(polaris.getAccessManager()).checkAccess(eq(USERID),
                    anyCollectionOf(UniqueID.class), anyVararg());
        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(new RestObject(rootSID, folder2).toStringFormal(),
                        "test")))
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when().put(getApiFilesURL() + new RestObject(sid1, file1).toStringFormal());

    }

    @Test
    public void shouldReturn403WhenTriesToMoveWithreadOnlyToken() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");

        givenReadAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(new RestObject(rootSID, folder1).toStringFormal(),
                        "folder2")))
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when().put(getApiFilesURL() + new RestObject(rootSID, file1).toStringFormal());
    }

    @Test
    public void shouldReturn204ForDeleteSuccess() throws Exception
    {
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");
        givenAccess()
        .expect()
                .statusCode(204)
        .when().delete(getApiFilesURL() + new RestObject(rootSID, file1).toStringFormal());
    }


    @Test
    public void shouldReturn404ForDeleteNonExistent() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
        .when().delete(getApiFilesURL() + new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn403WhenViewerTriesToDelete() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, folder1, "file1");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, folder1).jobID, 5);
        SID sid1 = SID.folderOID2convertedStoreSID(folder1);

        doThrow(new AccessException(USERID, sid1, Access.WRITE))
                .when(polaris.getAccessManager()).checkAccess(eq(USERID),
                    anyCollectionOf(UniqueID.class), anyVararg());

        givenAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when().delete(getApiFilesURL() + new RestObject(rootSID, file1).toStringFormal());
    }

    @Test
    public void shouldReturn403WhenTriesToDeleteWithReadOnlyToken() throws Exception
    {
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");

        givenReadAccess()
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when().delete(getApiFilesURL() + new RestObject(rootSID, file1).toStringFormal());
    }
}