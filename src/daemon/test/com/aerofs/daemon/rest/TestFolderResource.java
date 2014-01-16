package com.aerofs.daemon.rest;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.mock.logical.MockDS.MockDSDir;
import com.aerofs.daemon.rest.util.RestObject;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.SOID;
import com.aerofs.rest.api.*;
import com.aerofs.rest.api.Error;
import com.google.common.util.concurrent.SettableFuture;
import com.jayway.restassured.http.ContentType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

public class TestFolderResource extends AbstractRestTest
{
    private final String RESOURCE = "/v0.9/folders/{folder}";

    public TestFolderResource(boolean useProxy)
    {
        super(useProxy);
    }

    @Test
    public void shouldReturn400ForInvalidId() throws Exception
    {
        givenAcces()
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when().get(RESOURCE, "bla");
    }

    @Test
    public void shouldReturn404ForNonExistingStore() throws Exception
    {
        givenAcces()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, new RestObject(SID.generate(), OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn404ForNonExistingDir() throws Exception
    {
        givenAcces()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldGetMetadata() throws Exception
    {
        mds.root().dir("d0").file("f1");

        givenAcces()
        .expect()
                .statusCode(200)
                .body("id", equalTo(object("d0").toStringFormal()))
                .body("name", equalTo("d0"))
                .body("is_shared", equalTo(false))
        .when().get(RESOURCE, object("d0").toStringFormal());
    }

    @Test
    public void shouldReturn404ForFile() throws Exception
    {
        mds.root().file("f1");

        givenAcces()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, object("f1").toStringFormal());
    }

    @Test
    public void shouldCreate() throws Exception
    {
        SettableFuture<SOID> soid = whenCreate(Type.DIR, "", "foo");

        givenAcces()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "foo")))
        .expect()
                .statusCode(201)
                .body("id", equalToFutureObject(soid))
                .body("name", equalTo("foo"))
        .when().log().everything()
                .post("/v0.10/folders");
    }

    @Test
    public void shouldReturn403WhenViewerTriesToCreate() throws Exception
    {
        doThrow(new ExNoPerm()).when(acl).checkThrows_(
                user, mds.root().soid().sidx(), Permissions.EDITOR);

        givenAcces()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "foo")))
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .post("/v0.10/folders");
    }

    @Test
    public void shouldReturn404WhenTryingToCreateUnderNonExistingParent() throws Exception
    {
        givenAcces()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, OID.generate()).toStringFormal(), "foo")))
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .post("/v0.10/folders");
    }

    @Test
    public void shouldReturn404WhenTryingToCreateUnderFile() throws Exception
    {
        mds.root().file("f1");

        when(oc.create_(any(Type.class), any(SOID.class), anyString(), eq(PhysicalOp.APPLY), eq(t)))
                .thenThrow(new ExNotDir());

        givenAcces()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("f1").toStringFormal(), "foo")))
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .post("/v0.10/folders");
    }

    @Test
    public void shouldReturn409WhenAlreadyExists() throws Exception
    {
        whenCreate(Type.DIR, "", "foo");

        givenAcces()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "foo")))
        .expect()
                .statusCode(201)
        .when()
                .post("/v0.10/folders");

        givenAcces()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "foo")))
        .expect()
                .statusCode(409)
                .body("type", equalTo(Error.Type.CONFLICT.toString()))
        .when().log().everything()
                .post("/v0.10/folders");
    }

    @Test
    public void shouldMoveFolder() throws Exception
    {
        // create first folder
        MockDSDir d = mds.root().dir("foo");
        SOID firstSoid = d.soid();

        // create second folder
        MockDSDir d2 = mds.root().dir("boo");
        SOID secondSoid = d2.soid();

        // move foo into boo and rename it to moo
        String firstFolderIdStr = new RestObject(rootSID, firstSoid.oid()).toStringFormal();
        final String newFolderName = "moo";
        SettableFuture<SOID> newObjectId = whenMove("foo", "boo", newFolderName);
        givenAcces()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, secondSoid.oid()).toStringFormal(), newFolderName)))
        .expect()
                .statusCode(200)
                .body("id", equalToFutureObject(newObjectId))
                .body("name", equalTo(newFolderName))
        .when()
                .put("/v0.10/folders/" + firstFolderIdStr);

        assertEquals("Object id has changed", firstSoid, newObjectId.get());
    }

    @Test
    public void shouldReturn409WhenMoveConflict() throws Exception
    {
        // create first folder
        MockDSDir d = mds.root().dir("foo");
        SOID firstSoid = d.soid();

        String newFolderName = "boo";
        // create second folder
        mds.root().dir(newFolderName);

        // move foo into root and rename it to boo
        String firstFolderIdStr = new RestObject(rootSID, firstSoid.oid()).toStringFormal();

        whenMove("foo", "", newFolderName);

        givenAcces()
            .contentType(ContentType.JSON)
            .body(json(CommonMetadata.child(object("").toStringFormal(), newFolderName)))
        .expect()
            .statusCode(409)
            .body("type", equalTo(Error.Type.CONFLICT.toString()))
        .when()
            .put("/v0.10/folders/" + firstFolderIdStr);
    }

    @Test
    public void shouldReturn404MovingNonExistingDir() throws Exception
    {
        givenAcces()
            .contentType(ContentType.JSON)
            .body(json(CommonMetadata.child(object("").toStringFormal(), "test")))
        .expect()
            .statusCode(404)
            .body("type", equalTo("NOT_FOUND"))
        .when()
            .put("/v0.10/folders/" + new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn404MovingToNonExistingParrent() throws Exception
    {
        // create folder
        MockDSDir d = mds.root().dir("foo");
        SOID soid = d.soid();
        String folderIdStr = new RestObject(rootSID, soid.oid()).toStringFormal();
        givenAcces()
            .contentType(ContentType.JSON)
            .body(json(CommonMetadata.child(new RestObject(rootSID,
                    OID.generate()).toStringFormal(), "test")))
        .expect()
            .statusCode(404)
            .body("type", equalTo("NOT_FOUND"))
        .when()
            .put("/v0.10/folders/" + folderIdStr);
    }

    @Test
    public void shouldReturn403WhenViewerTriesToMove() throws Exception
    {
        MockDSDir item = mds.root().dir("foo");
        SOID soid = item.soid();
        doThrow(new ExNoPerm()).when(acl).checkThrows_(
                user, soid.sidx(), Permissions.EDITOR);
        String idStr = new RestObject(rootSID, soid.oid()).toStringFormal();
        whenMove("foo", "", "moo");
        givenAcces()
            .contentType(ContentType.JSON)
            .body(json(CommonMetadata.child(object("").toStringFormal(), "moo")))
        .expect()
            .statusCode(403)
            .body("type", equalTo("FORBIDDEN"))
        .when()
            .put("/v0.10/folders/" + idStr);
    }

    @Test
    public void shouldReturn204ForDeleteSuccess() throws Exception
    {
        // create folder
        MockDSDir d = mds.root().dir("foo");
        SOID soid = d.soid();
        String folderIdStr = new RestObject(rootSID, soid.oid()).toStringFormal();
        givenAcces()
        .expect()
                .statusCode(204)
        .when()
                .delete("/v0.10/folders/" + folderIdStr);

    }

    @Test
    public void shouldReturn404ForDeleteNonExistent() throws Exception
    {
        givenAcces()
        .expect()
            .statusCode(404)
        .when()
            .delete("/v0.10/folders/" + new RestObject(rootSID, OID.generate()).toStringFormal());

    }

    @Test
    public void shouldReturn403WhenViewerTriesToDelete() throws Exception
    {
        doThrow(new ExNoPerm()).when(acl).checkThrows_(
                user, mds.root().soid().sidx(), Permissions.EDITOR);
        MockDSDir item = mds.root().dir("foo");
        SOID soid = item.soid();
        String idStr = new RestObject(rootSID, soid.oid()).toStringFormal();
        doThrow(new ExNoPerm()).when(acl).checkThrows_(
                user, soid.sidx(), Permissions.EDITOR);
        givenAcces()
        .expect()
            .statusCode(403)
            .body("type", equalTo("FORBIDDEN"))
        .when()
            .delete("/v0.10/folders/" + idStr);
    }

}
