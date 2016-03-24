package com.aerofs.polaris.external_api.rest;

import com.aerofs.base.id.RestObject;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.rest.api.CommonMetadata;
import com.aerofs.rest.api.Error;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.internal.mapper.ObjectMapperType;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.junit.Test;

import static com.aerofs.polaris.PolarisTestServer.getApiFoldersURL;
import static org.hamcrest.Matchers.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class TestFolderResource extends AbstractRestTest
{
    @Test
    public void shouldReturn400ForInvalidId() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .get(getApiFoldersURL() + "bla");
    }

    @Test
    public void shouldReturn404ForNonExistingStore() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .get(getApiFoldersURL() + new RestObject(SID.generate(), OID.generate())
                        .toStringFormal());
    }

    @Test
    public void shouldReturn404ForNonExistingDir() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .get(getApiFoldersURL() + new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn404ForFile() throws Exception
    {
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");
        RestObject objectFile1 = new RestObject(rootSID, file1);

        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .get(getApiFoldersURL() + objectFile1.toStringFormal());
    }

    @Test
    public void shouldGetMetadata() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        RestObject restFolder1 = new RestObject(rootSID, folder1);

        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(restFolder1.toStringFormal()))
                .body("name", equalTo("folder1"))
                .body("is_shared", equalTo(false))
        .when()
                .get(getApiFoldersURL() + restFolder1.toStringFormal());
    }

    @Test
    public void shouldGetMetadataForSID() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, folder1);
        RestObject restShared1 = new RestObject(SID.folderOID2convertedStoreSID(folder1), OID.ROOT);

        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(new RestObject(rootSID,
                        SID.folderOID2convertedAnchorOID(folder1)).toStringFormal()))
                .body("name", equalTo("folder1"))
                .body("is_shared", equalTo(true))
                .body("sid", equalTo(restShared1.getSID().toStringFormal()))
        .when().get(getApiFoldersURL() + restShared1.toStringFormal());
    }

    @Test
    public void shouldGetMetadataForAnchor() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, folder1);
        RestObject restShared1 = new RestObject(SID.folderOID2convertedStoreSID(folder1), OID.ROOT);
        RestObject restAnchor1 = new RestObject(rootSID,
                SID.folderOID2convertedAnchorOID(folder1));

        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(restAnchor1.toStringFormal()))
                .body("name", equalTo("folder1"))
                .body("is_shared", equalTo(true))
                .body("sid", equalTo(restShared1.getSID().toStringFormal()))
        .when().get(getApiFoldersURL() + restAnchor1.toStringFormal());
    }

    @Test
    public void shouldGetMetadataForRootStoreWhenGivenRootAlias() throws Exception
    {
        PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        String oid = rootSID.toStringFormal() + OID.ROOT.toStringFormal();

        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(oid))
                .body("name", equalTo("AeroFS"))
                .body("is_shared", equalTo(false))
                .body("parent", equalTo(oid))
        .when().get(getApiFoldersURL() + "root");
    }

    @Test
    public void shouldGetMetadataForRootStoreWhenGivenUserID() throws Exception
    {
        PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        String oid = rootSID.toStringFormal() + OID.ROOT.toStringFormal();

        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(oid))
                .body("name", equalTo("AeroFS"))
                .body("is_shared", equalTo(false))
                .body("parent", equalTo(oid))
        .when()
                .get(getApiFoldersURL() + USERID.getString()).prettyPrint();
    }

    @Test
    public void shouldGetMetadataForRootStore() throws Exception
    {
        PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        String restObject = rootSID.toStringFormal() + OID.ROOT.toStringFormal();

        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(restObject))
                .body("name", equalTo("AeroFS"))
                .body("is_shared", equalTo(false))
                .body("parent", equalTo(restObject))
        .when()
                .get(getApiFoldersURL() + rootSID.toStringFormal()).prettyPrint();
    }

    @Test
    public void shouldMoveFolderUnderAnchor() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder2");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, folder2).jobID, 5);

        SID sid2 = SID.folderOID2convertedStoreSID(folder2);
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
               .put(getApiFoldersURL() + new RestObject(rootSID, folder1).toStringFormal());
    }

    @Test
    public void shouldGetMetadataForExternalStore() throws Exception
    {
        SID sid = SID.generate();
        // This is necessary to make polaris aware of the external store.
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, sid, "file1");
        String oid = sid.toStringFormal() + OID.ROOT.toStringFormal();

        givenAccess()
                .queryParam("fields", "children")
        .expect()
            .statusCode(200)
            .body("id", equalTo(oid))
            .body("name", equalTo(""))
            .body("is_shared", equalTo(true))
            .body("parent", equalTo(oid))
            .body("children.files", iterableWithSize(1))
            .body("children.files[0].id", equalTo(new RestObject(sid, file1)
                    .toStringFormal()))
        .when()
                .get(getApiFoldersURL() + sid.toStringFormal());
    }

    @Test
    public void shouldGetMetadataWithOnDemandFields() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, folder1, "file1");
        RestObject restFolder1 = new RestObject(rootSID, folder1);

        givenAccess()
                .queryParam("fields", "path,children")
        .expect()
                .statusCode(200)
                .body("id", equalTo(restFolder1.toStringFormal()))
                .body("name", equalTo("folder1"))
                .body("is_shared", equalTo(false))
                .body("path.folders", iterableWithSize(1))
                .body("path.folders[0].name", equalTo("AeroFS"))
                .body("path.folders[0].id", equalTo(new RestObject(rootSID, OID.ROOT)
                        .toStringFormal()))
                .body("path.folders[0].is_shared", equalTo(false))
                .body("children.folders", emptyIterable())
                .body("children.files", iterableWithSize(1))
                .body("children.files[0].id", equalTo(new RestObject(rootSID, file1)
                        .toStringFormal()))
                .body("children.files[0].name", equalTo("file1"))
        .when()
                .get(getApiFoldersURL() + restFolder1.toStringFormal());
    }

    @Test
    public void shouldGetMetadataWithOnDemandFieldsForSID() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, folder1, "file1");
        PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, folder1);
        SID sid = SID.folderOID2convertedStoreSID(folder1);
        RestObject restShared1 = new RestObject(rootSID, SID.folderOID2convertedAnchorOID(folder1));
        RestObject objectShared1 = new RestObject(sid, OID.ROOT);

        givenAccess()
                .queryParam("fields", "path,children")
        .expect()
                .statusCode(200)
                .body("id", equalTo(restShared1.toStringFormal()))
                .body("name", equalTo("folder1"))
                .body("is_shared", equalTo(true))
                .body("path.folders", iterableWithSize(1))
                .body("path.folders[0].name", equalTo("AeroFS"))
                .body("path.folders[0].id", equalTo(new RestObject(rootSID, OID.ROOT)
                        .toStringFormal()))
                .body("path.folders[0].is_shared", equalTo(false))
                .body("children.folders", emptyIterable())
                .body("children.files", iterableWithSize(1))
                .body("children.files[0].id", equalTo(new RestObject(sid, file1).toStringFormal()))
                .body("children.files[0].name", equalTo("file1"))
        .when()
                .get(getApiFoldersURL() + objectShared1.toStringFormal());
    }

    @Test
    public void shouldReturnEmptyPathForRootExplicit() throws Exception
    {
        PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");

        givenAccess()
        .expect()
                .statusCode(200)
                .body("folders", emptyIterable())
        .when().get(getApiFoldersURL() + new RestObject(rootSID, OID.ROOT).toStringFormal() + "/path");
    }

    @Test
    public void shouldReturnEmptyPathForRootImplicit() throws Exception
    {
        PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");

        givenAccess()
        .expect()
                .statusCode(200)
                .body("folders", emptyIterable())
        .when().get(getApiFoldersURL() + "root" + "/path");
    }

    @Test
    public void shouldReturnPath() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder11 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder11");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, folder11, "folder2");

        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, folder1, folder11).jobID, 10);
        SID sid11 = SID.folderOID2convertedStoreSID(folder11);

        RestObject restShared11 = new RestObject(rootSID, SID.folderOID2convertedAnchorOID(folder11));
        RestObject objectRoot = new RestObject(rootSID, OID.ROOT);
        RestObject objectFolder1 = new RestObject(rootSID, folder1);
        RestObject objectFolder2 = new RestObject(sid11, folder2);

        givenAccess()
        .expect()
                .statusCode(200)
                .body("folders[0].id", equalTo(objectRoot.toStringFormal()))
                .body("folders[0].name", equalTo("AeroFS"))
                .body("folders[0].is_shared", equalTo(false))

                .body("folders[1].id", equalTo(objectFolder1.toStringFormal()))
                .body("folders[1].name", equalTo("folder1"))
                .body("folders[1].is_shared", equalTo(false))
                .body("folders[1].parent", equalTo(objectRoot.toStringFormal()))

                .body("folders[2].id", equalTo(restShared11.toStringFormal()))
                .body("folders[2].name", equalTo("folder11"))
                .body("folders[2].is_shared", equalTo(true))
                .body("folders[2].sid", equalTo(sid11.toStringFormal()))
                .body("folders[2].parent", equalTo(objectFolder1.toStringFormal()))
        .when()
                .get(getApiFoldersURL() + objectFolder2.toStringFormal() + "/path");
    }


    @Test
    public void shouldReturn404ForInvalidPath() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .get(getApiFoldersURL() + new RestObject(rootSID, OID.generate()).toStringFormal()
                        + "/path");
    }

    @Test
    public void shouldListRoot() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder2");
        PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");

        PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, folder1);

        givenAccess()
        .expect()
                .statusCode(200)
                .body("files", hasSize(1)).body("files.name", hasItems("file1"))
                .body("folders", hasSize(2)).body("folders.name", hasItems("folder1", "folder2"))
        .when()
                .get(getApiFoldersURL() + "root" + "/children");
    }

    @Test
    public void shouldListChildren() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder2");
        PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder3");
        PolarisHelpers.newFile(AUTHENTICATED, folder1, "file1");

        PolarisHelpers.shareFolder(AUTHENTICATED, folder1, folder2);

        RestObject objectFolder1 = new RestObject(rootSID, folder1);

        givenAccess()
        .expect()
                .statusCode(200)
                .body("files", hasSize(1)).body("files.name", hasItems("file1"))
                .body("folders", hasSize(2)).body("folders.name", hasItems("folder2", "folder3"))
        .when()
                .get(getApiFoldersURL() + objectFolder1.toStringFormal() + "/children");
    }

    @Test
    public void shouldReturn404WhenListNonExistingDir() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(getApiFoldersURL() +
                new RestObject(rootSID, OID.generate()).toStringFormal() + "/children");
    }

    @Test
    public void shouldCreate() throws Exception
    {
        // TODO(AS): Need something to compare id returned in the response body. Right now
        // not doing that because create end point in FoldersResource calls OID.generate()
        // whose value cannot be verified. Need a way to make it return a value that can be verified
        // against.
        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(new RestObject(rootSID, OID.ROOT).toStringFormal(),
                            "foo")))
        .expect()
                .statusCode(201)
                .body("name", equalTo("foo"))
                .body("is_shared", equalTo(false))
        .when().log().everything().post(getApiFoldersURL());
    }

    @Test
    public void shouldCreateUnderRoot() throws Exception
    {
        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child("root", "foo")))
        .expect()
                .statusCode(201)
                .body("name", equalTo("foo"))
                .body("is_shared", equalTo(false))
        .when()
                .post(getApiFoldersURL());
    }

    @Test
    public void shouldCreateUnderAnchor() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, folder1);
        RestObject restShared1 = new RestObject(rootSID, SID.folderOID2convertedAnchorOID(folder1));

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(restShared1.toStringFormal(), "foo")))
        .expect()
                .statusCode(201)
                .body("name", equalTo("foo"))
                .body("is_shared", equalTo(false))
        .when()
               .post(getApiFoldersURL());
    }

    @Test
    public void shouldReturn403WhenViewerTriesToCreate() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, folder1).jobID, 5);
        SID sid1 = SID.folderOID2convertedStoreSID(folder1);
        RestObject objectFolder1 = new RestObject(sid1, OID.ROOT);

        doThrow(new AccessException(USERID, sid1, Access.WRITE))
                .when(polaris.getAccessManager()).checkAccess(eq(USERID), anyCollectionOf(UniqueID.class),
                    anyVararg());

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(objectFolder1.toStringFormal(), "foo")))
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .post(getApiFoldersURL());
    }

    @Test
    public void shouldReturn403WhenTriesToCreateWithReadOnlyToken() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, folder1);
        SID sid1 = SID.folderOID2convertedStoreSID(folder1);
        RestObject objectFolder1 = new RestObject(sid1, OID.ROOT);

        givenReadAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(objectFolder1.toStringFormal(), "foo")))
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .post(getApiFoldersURL());
    }

    @Test
    public void shouldReturn404WhenTryingToCreateUnderNonExistingParent() throws Exception
    {
        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(new RestObject(rootSID, OID.generate()).toStringFormal(),
                        "foo")))
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .post(getApiFoldersURL());
    }

    @Test
    public void shouldReturn400WhenTryingToCreateUnderFile() throws Exception
    {
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(CommonMetadata.child(new RestObject(rootSID, file1).toStringFormal(), "foo"),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .post(getApiFoldersURL());
    }

    @Test
    public void shouldReturn409WhenAlreadyExists() throws Exception
    {
        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(new RestObject(rootSID, OID.ROOT).toStringFormal(),
                        "foo")))
        .expect()
                .statusCode(201)
        .when().post(getApiFoldersURL());

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(new RestObject(rootSID, OID.ROOT).toStringFormal(),
                        "foo")))
        .expect()
                .statusCode(409)
                .body("type", equalTo(com.aerofs.rest.api.Error.Type.CONFLICT.toString()))
        .when()
                .post(getApiFoldersURL());
    }

    @Test
    public void shouldMoveFolder() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder2");

        // TODO(AS): Again need to verify after moving object.
        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, folder2).toStringFormal(), "moved")))
        .expect()
                .statusCode(200)
                .body("id", equalTo(new RestObject(rootSID, folder1).toStringFormal()))
                .body("name", equalTo("moved"))
                .body("parent", equalTo(new RestObject(rootSID, folder2).toStringFormal()))
        .when()
                .put(getApiFoldersURL() + new RestObject(rootSID, folder1).toStringFormal());

    }

    @Test
    public void shouldReturn409WhenMoveConflict() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder2");
        PolarisHelpers.newFolder(AUTHENTICATED, folder2, "temp");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, folder2).toStringFormal(), "temp")))
        .expect()
                .statusCode(409)
                .body("type", equalTo(Error.Type.CONFLICT.toString()))
        .when()
                .put(getApiFoldersURL() + new RestObject(rootSID, folder1).toStringFormal());
    }

    @Test
    public void shouldReturn404MovingNonExistingDir() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");

        givenAccess()
                .contentType(ContentType.JSON)
                .header(Names.IF_MATCH, OTHER_ETAG)
                .body(json(CommonMetadata.child(new RestObject(rootSID, folder1).toStringFormal(),
                        "bar")))
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .put(getApiFoldersURL() + new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn404MovingToNonExistingParent() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, OID.generate()).toStringFormal(), "test")))
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .put(getApiFoldersURL() + new RestObject(rootSID, folder1).toStringFormal());
    }

    @Test
    public void shouldReturn400MovingUnderSelf() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, folder1).toStringFormal(), "test")))
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put(getApiFoldersURL() + new RestObject(rootSID, folder1).toStringFormal());
    }

    @Test
    public void shouldReturn400MovingUnderOwnChild() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder11 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder11");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, folder11).toStringFormal(), "test")))
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put(getApiFoldersURL() + new RestObject(rootSID, folder1).toStringFormal());
    }

    @Test
    public void shouldReturn400MovingRoot() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, folder1).toStringFormal(),
                        "test")))
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put(getApiFoldersURL() + "root");
    }

    @Test
    public void shouldReturn400MovingUnderFile() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, folder1, "file1");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(new RestObject(rootSID, file1).toStringFormal(),
                        "bar")))
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put(getApiFoldersURL() + new RestObject(rootSID, folder1).toStringFormal());
    }

    @Test
    public void shouldReturn403WhenViewerTriesToMove() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder11 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder11");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, folder1).jobID, 5);
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
        .when()
                .put(getApiFoldersURL() + new RestObject(sid1, folder11).toStringFormal());
    }

    @Test
    public void shouldReturn403WhenTriesToMoveWithReadOnlyToken() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder2");

        givenReadAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(new RestObject(rootSID, folder2).toStringFormal(),
                        "folder2")))
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .put(getApiFoldersURL() + new RestObject(rootSID, folder1).toStringFormal());
    }

    @Test
    public void shouldReturn204ForDeleteSuccess() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");

        givenAccess()
        .expect()
                .statusCode(204)
        .when()
                .delete(getApiFoldersURL() + new RestObject(rootSID, folder1).toStringFormal());
    }

    @Test
    public void shouldReturn404ForDeleteNonExistent() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
        .when()
                .delete(getApiFoldersURL() + new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn403WhenViewerTriesToDelete() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder11 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder11");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, folder1).jobID, 5);
        SID sid1 = SID.folderOID2convertedStoreSID(folder1);

        doThrow(new AccessException(USERID, sid1, Access.WRITE))
                .when(polaris.getAccessManager()).checkAccess(eq(USERID),
                        anyCollectionOf(UniqueID.class), anyVararg());

        givenAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .delete(getApiFoldersURL() + new RestObject(rootSID, folder11).toStringFormal());
    }

    @Test
    public void shouldReturn403WhenTriesToDeleteWithReadOnlyToken() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");

        givenReadAccess()
                .contentType(ContentType.JSON)
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .delete(getApiFoldersURL() + new RestObject(rootSID, folder1).toStringFormal());
    }

    @Test
    public void shouldReturn403WhenGetMetadataForAnchorWithNoPermWithLinkShareToken() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder2");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, folder1, folder2).jobID, 5);
        SID sid2 = SID.folderOID2convertedStoreSID(folder2);
        RestObject restAnchor1 = new RestObject(rootSID,
                SID.folderOID2convertedAnchorOID(folder2));

        doThrow(new AccessException(USERID, sid2, Access.MANAGE))
                .when(polaris.getAccessManager()).checkAccess(eq(USERID),
                        anyCollectionOf(UniqueID.class), anyVararg());

        givenLinkShareReadAccess()
        .expect()
                .statusCode(403)
        .when()
                .get(getApiFoldersURL() + restAnchor1.toStringFormal());
    }

    @Test
    public void shouldReturn403WhenGetMetadataForStoreWithNoPermWithLinkShareToken() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder2");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, folder1, folder2).jobID, 5);
        SID sid2 = SID.folderOID2convertedStoreSID(folder2);

        doThrow(new AccessException(USERID, sid2, Access.MANAGE))
                .when(polaris.getAccessManager()).checkAccess(eq(USERID),
                        anyCollectionOf(UniqueID.class), anyVararg());

        givenLinkShareReadAccess()
        .expect()
                .statusCode(403)
        .when()
                .get(getApiFoldersURL() + new RestObject(sid2, OID.ROOT).toStringFormal());
    }

    @Test
    public void shouldReturn200WhenGetMetadataForAnchorWithPermWithLinkShareToken() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, folder1).jobID, 5);
        RestObject restAnchor1 = new RestObject(rootSID,
                SID.folderOID2convertedAnchorOID(folder1));

        doNothing().when(polaris.getAccessManager())
                .checkAccess(eq(USERID), anyListOf(UniqueID.class), anyVararg());

        givenLinkShareReadAccess()
        .expect()
                .statusCode(200)
                .body("name", equalTo("folder1"))
        .when()
                .get(getApiFoldersURL() + restAnchor1.toStringFormal());
    }

    @Test
    public void shouldReturn200WhenGetMetadataForStoreWithPermWithLinkShareToken() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, folder1).jobID, 5);
        SID sid1 = SID.folderOID2convertedStoreSID(folder1);

        doNothing().when(polaris.getAccessManager())
                .checkAccess(eq(USERID), anyListOf(UniqueID.class), anyVararg());

        givenLinkShareReadAccess()
        .expect()
                .statusCode(200)
                .body("name", equalTo("folder1"))
        .when()
                .get(getApiFoldersURL() + new RestObject(sid1, OID.ROOT).toStringFormal());
    }

    @Test
    public void shouldHideChildAnchorWithNoPermWithLinkShareToken() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder2");
        PolarisHelpers.newFolder(AUTHENTICATED, folder2, "folder3");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, folder1, folder2).jobID, 5);
        SID sid2 = SID.folderOID2convertedStoreSID(folder2);

        doNothing().doThrow(new AccessException(USERID, sid2, Access.MANAGE)).when(polaris.getAccessManager())
                .checkAccess(eq(USERID), anyCollectionOf(UniqueID.class), anyVararg());

        givenLinkShareReadAccess()
                .queryParam("fields", "children")
        .expect()
                .statusCode(200)
                .body("name", equalTo("folder1"))
                .body("children.folders", iterableWithSize(0))
        .when()
                .get(getApiFoldersURL() + new RestObject(rootSID, folder1).toStringFormal());
    }

    @Test
    public void shouldShowChildAnchorWithPermWithLinkShareToken() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder2");
        PolarisHelpers.newFolder(AUTHENTICATED, folder2, "folder3");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, folder1, folder2).jobID, 5);

        doNothing().when(polaris.getAccessManager())
                .checkAccess(eq(USERID), anyCollectionOf(UniqueID.class), anyVararg());

        givenLinkShareReadAccess()
                .queryParam("fields", "children")
        .expect()
                .statusCode(200)
                .body("name", equalTo("folder1"))
                .body("children.folders", iterableWithSize(1))
                .body("children.folders[0].id", equalTo(new RestObject(rootSID,
                        SID.folderOID2convertedAnchorOID(folder2)).toStringFormal()))
                .body("children.folders[0].name", equalTo("folder2"))
        .when()
                .get(getApiFoldersURL() + new RestObject(rootSID, folder1).toStringFormal());
    }

    @Test
    public void shouldReturn404IfAnyParentDeleted() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder2");
        OID folder3 = PolarisHelpers.newFolder(AUTHENTICATED, folder2, "folder3");
        OID folder4 = PolarisHelpers.newFolder(AUTHENTICATED, folder3, "folder4");
        PolarisHelpers.removeFileOrFolder(AUTHENTICATED, folder1, folder2);

        givenAccess()
        .expect()
                .statusCode(404)
        .when().get(getApiFoldersURL() + new RestObject(rootSID, folder4).toStringFormal());
    }

    @Test
    public void shouldGetMetadataForSubfolderInExternalStore() throws Exception
    {
        SID sid = SID.generate();
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, sid, "folder1");
        String oid = sid.toStringFormal() + folder1.toStringFormal();

        givenAccess()
                .queryParam("fields", "path,children")
        .expect()
                .statusCode(200)
                .body("id", equalTo(oid))
                .body("name", equalTo("folder1"))
                .body("is_shared", equalTo(false))
                .body("path.folders", iterableWithSize(1))
                .body("path.folders[0].name", equalTo(""))
                .body("path.folders[0].id", equalTo(new RestObject(sid, OID.ROOT)
                        .toStringFormal()))
                .body("path.folders[0].is_shared", equalTo(true))
                .body("parent", equalTo(new RestObject(sid, OID.ROOT).toStringFormal()))
        .when().get(getApiFoldersURL() + oid);
    }

    @Test
    public void shouldShareExistingFolder() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        RestObject restFolder1 = new RestObject(rootSID, folder1);
        OID sid1 = SID.folderOID2convertedAnchorOID(folder1);
        RestObject expected = new RestObject(rootSID, sid1);

        doReturn(true).when(polaris.getFolderSharer()).shareFolder(any(), any(), any());

        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(expected.toStringFormal()))
                .body("name", equalTo("folder1"))
                .body("is_shared", equalTo(true))
                .body("sid", equalTo(sid1.toStringFormal()))
        .when()
                .put(getApiFoldersURL() + restFolder1.toStringFormal() + "/is_shared");
    }

    @Test
    public void should400WhenTryingToShareChildFolderOfSharedFolder() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED,
                PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, folder1).jobID, 5);
        SID sid1 = SID.folderOID2convertedStoreSID(folder1);

        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, sid1, "folder2");
        RestObject restFolder2 = new RestObject(sid1, folder2);
        givenAccess()
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put(getApiFoldersURL() + restFolder2.toStringFormal() + "/is_shared");
    }

    @Test
    public void should400WhenTryingToShareParentFolderOfSharedFolder() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder2");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED,
                PolarisHelpers.shareFolder(AUTHENTICATED, folder1, folder2).jobID, 5);
        RestObject restFolder1 = new RestObject(rootSID, folder1);
        givenAccess()
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put(getApiFoldersURL() + restFolder1.toStringFormal() + "/is_shared");
    }

    @Test
    public void shouldStillCallSpartaForExistingSharedFolder() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED,
                PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, folder1).jobID, 5);

        doReturn(true).when(polaris.getFolderSharer()).shareFolder(any(), any(), any());

        givenAccess()
        .expect()
                .statusCode(200)
        .when()
                .put(getApiFoldersURL() + new RestObject(rootSID, folder1).toStringFormal() + "/is_shared");
        verify(polaris.getFolderSharer()).shareFolder(any(), any(), any());
    }

    @Test
    public void shouldHandleUnicodeNames() throws Exception
    {
        OID folder1= PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder");
        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, OID.ROOT).toStringFormal(), "ㅂㅈㄷㄱ")))
        .expect()
                .statusCode(200)
        .when().log().everything()
                .put(getApiFoldersURL() + new RestObject(rootSID, folder1).toStringFormal());
    }

    @Test
    public void shouldMoveFolderWhenEtagMatch() throws Exception
    {
        OID folder = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder");
        OID parent = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "parent");

        givenAccess()
                .contentType(ContentType.JSON)
                .header(Names.IF_MATCH, getFolderEtag(rootSID, folder))
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, parent).toStringFormal(), "foo")))
        .expect()
                .statusCode(200)
                .body("id", equalTo(new RestObject(rootSID, folder).toStringFormal()))
                .body("name", equalTo("foo"))
        .when()
                .put(getApiFoldersURL() + new RestObject(rootSID, folder).toStringFormal());
    }

    @Test
    public void shouldReturn412WhenMovingAndEtagChanged() throws Exception
    {
        OID folder = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder");
        OID parent = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "parent");

        givenAccess()
                .contentType(ContentType.JSON)
                .header(Names.IF_MATCH, OTHER_ETAG)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, parent).toStringFormal(), "foo")))
        .expect()
                .statusCode(412)
                .body("type", equalTo("CONFLICT"))
        .when()
                .put(getApiFoldersURL() + new RestObject(rootSID, folder).toStringFormal());
    }


    @Test
    public void shouldReturn204WhenDeletingAndEtagMatch() throws Exception
    {
        OID folder = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder");

        givenAccess()
                .header(Names.IF_MATCH, getFolderEtag(rootSID, folder))
        .expect()
                .statusCode(204)
        .when()
                .delete(getApiFoldersURL() + new RestObject(rootSID, folder).toStringFormal());
    }

    @Test
    public void shouldReturn412WhenDeletingAndEtagChanged() throws Exception
    {
        OID folder = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder");

        givenAccess()
                .header(Names.IF_MATCH, OTHER_ETAG)
        .expect()
                .statusCode(412)
                .body("type", equalTo("CONFLICT"))
        .when()
                .delete(getApiFoldersURL() + new RestObject(rootSID, folder).toStringFormal());
    }

    @Test
    public void shouldGetCORSHeadersInReponse() throws Exception
    {
        PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");

        givenAccess()
        .expect()
                .statusCode(200)
                .header(Names.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                // TODO(AS): This checks if this header is not null. Since its added in the CORS
                // filter, I believe it is a sufficient check. However, we are not checking the exact
                // header value.
                .header(Names.ACCESS_CONTROL_EXPOSE_HEADERS, notNullValue(String.class))
        .when()
                .get(getApiFoldersURL() + rootSID.toStringFormal());
    }
}
