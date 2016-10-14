package com.aerofs.daemon.rest;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.ids.OID;
import com.aerofs.base.id.RestObject;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.rest.api.CommonMetadata;
import com.aerofs.rest.api.Error;
import com.google.common.util.concurrent.SettableFuture;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.internal.mapper.ObjectMapperType;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.junit.Test;

import java.util.Properties;

import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

public class TestFolderResource extends AbstractRestTest
{
    private final String RESOURCE = "/v1.2/folders/{folder}";

    public TestFolderResource(boolean useProxy)
    {
        super(useProxy);
    }

    @Test
    public void shouldReturn400ForInvalidId() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when().get(RESOURCE, "bla");
    }

    @Test
    public void shouldReturn404ForNonExistingStore() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().log().everything()
                .get(RESOURCE, new RestObject(SID.generate(), OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn404ForNonExistingDir() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldGetMetadata() throws Exception
    {
        mds.root().dir("d0").file("f1");

        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(object("d0").toStringFormal()))
                .body("name", equalTo("d0"))
                .body("is_shared", equalTo(false))
        .when().get(RESOURCE, object("d0").toStringFormal());
    }

    @Test
    public void shouldGetMetadataForAnchor() throws Exception
    {
        mds.root().anchor("a0").file("f1");
        SID sid = SID.anchorOID2storeSID(ds.resolveNullable_(Path.fromString(rootSID, "a0")).oid());

        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(object("a0").toStringFormal()))
                .body("name", equalTo("a0"))
                .body("is_shared", equalTo(true))
                .body("sid", equalTo(sid.toStringFormal()))
        .when().get(RESOURCE, object("a0").toStringFormal());
    }

    @Test
    public void shouldGetMetadataForSID() throws Exception
    {
        mds.root().anchor("a0").file("f1");
        SID sid = SID.anchorOID2storeSID(ds.resolveNullable_(Path.fromString(rootSID, "a0")).oid());

        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(object("a0").toStringFormal()))
                .body("name", equalTo("a0"))
                .body("is_shared", equalTo(true))
                .body("sid", equalTo(sid.toStringFormal()))
        .when().get(RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldGetMetadataForRootStoreWhenGivenRootAlias() throws Exception
    {
        mds.root().anchor("a0").file("f1");
        SID sid = mds.root().getPath().sid();

        String oid = sid.toStringFormal() + OID.ROOT.toStringFormal();

        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(oid))
                .body("name", equalTo("AeroFS"))
                .body("is_shared", equalTo(false))
                .body("parent", equalTo(oid))
        .when().get(RESOURCE, "root").prettyPrint();
    }

    @Test
    public void shouldGetMetadataForRootStoreWhenGivenUserID() throws Exception
    {
        mds.root().anchor("a0").file("f1");
        SID sid = mds.root().getPath().sid();

        String oid = sid.toStringFormal() + OID.ROOT.toStringFormal();

        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(oid))
                .body("name", equalTo("AeroFS"))
                .body("is_shared", equalTo(false))
                .body("parent", equalTo(oid))
        .when().get(RESOURCE, user.getString()).prettyPrint();
    }

    @Test
    public void shouldGetMetadataForRootStore() throws Exception
    {
        mds.root().anchor("a0").file("f1");
        SID sid = mds.root().getPath().sid();

        String oid = sid.toStringFormal() + OID.ROOT.toStringFormal();

        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(oid))
                .body("name", equalTo("AeroFS"))
                .body("is_shared", equalTo(false))
                .body("parent", equalTo(oid))
        .when().get(RESOURCE, sid.toStringFormal()).prettyPrint();
    }

    @Test
    public void shouldGetMetadataForExternalStore() throws Exception
    {
        SID sid = SID.generate();
        mds.root(sid).file("f1");
        when(ss.getName_(sm.get_(sid))).thenReturn("baz");

        String oid = sid.toStringFormal() + OID.ROOT.toStringFormal();

        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(oid))
                .body("name", equalTo("baz"))
                .body("is_shared", equalTo(true))
                .body("parent", equalTo(oid))
        .when().get(RESOURCE, sid.toStringFormal()).prettyPrint();
    }

    @Test
    public void shouldGetMetadataWithOnDemandFields() throws Exception
    {
        mds.root().dir("d0").file("f1");

        givenAccess()
                .queryParam("fields", "path,children")
        .expect()
                .statusCode(200)
                .body("id", equalTo(object("d0").toStringFormal()))
                .body("name", equalTo("d0"))
                .body("is_shared", equalTo(false))
                .body("path.folders", iterableWithSize(1))
                .body("path.folders[0].name", equalTo("AeroFS"))
                .body("path.folders[0].id", equalTo(id(mds.root().soid())))
                .body("path.folders[0].is_shared", equalTo(false))
                .body("children.folders", emptyIterable())
                .body("children.files", iterableWithSize(1))
                .body("children.files[0].id", equalTo(object("d0/f1").toStringFormal()))
                .body("children.files[0].name", equalTo("f1"))
        .when().log().everything()
                .get(RESOURCE, object("d0").toStringFormal());
    }

    @Test
    public void shouldGetMetadataWithOnDemandFieldsForSID() throws Exception
    {
        mds.root().anchor("a0").file("f1");
        SID sid = SID.anchorOID2storeSID(mds.root().anchor("a0").soid().oid());

        givenAccess()
                .queryParam("fields", "path,children")
        .expect()
                .statusCode(200)
                .body("id", equalTo(object("a0").toStringFormal()))
                .body("name", equalTo("a0"))
                .body("is_shared", equalTo(true))
                .body("sid", equalTo(sid.toStringFormal()))
                .body("path.folders", iterableWithSize(1))
                .body("path.folders[0].name", equalTo("AeroFS"))
                .body("path.folders[0].id", equalTo(id(mds.root().soid())))
                .body("path.folders[0].is_shared", equalTo(false))
                .body("children.folders", emptyIterable())
                .body("children.files", iterableWithSize(1))
                .body("children.files[0].id", equalTo(object("a0/f1").toStringFormal()))
                .body("children.files[0].name", equalTo("f1"))
        .when().log().everything()
                .get(RESOURCE, sid.toStringFormal());
    }

    @Test
    public void shouldReturn404ForFile() throws Exception
    {
        mds.root().file("f1");

        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, object("f1").toStringFormal());
    }

    @Test
    public void shouldReturnEmptyPathForRootExplicit() throws Exception
    {
        mds.root()
                .dir("d1")
                .anchor("a2")
                .dir("d3");

        givenAccess()
                .expect()
                .statusCode(200)
                .body("folders", emptyIterable())
                .when().log().everything()
                .get(RESOURCE + "/path", object("").toStringFormal());
    }

    @Test
    public void shouldReturnEmptyPathForRootImplicit() throws Exception
    {
        mds.root()
                .dir("d1")
                .anchor("a2")
                .dir("d3");

        givenAccess()
                .expect()
                .statusCode(200)
                .body("folders", emptyIterable())
        .when().log().everything()
                .get(RESOURCE + "/path", "root");
    }

    @Test
    public void shouldReturnPath() throws Exception
    {
        mds.root().dir("d1").anchor("a2").dir("d3");
        SID sid = SID.anchorOID2storeSID(ds.resolveNullable_(Path.fromString(rootSID, "d1/a2")).oid());

        givenAccess()
        .expect()
                .statusCode(200)
                .body("folders[0].id", equalTo(id(mds.root().soid())))
                .body("folders[0].name", equalTo("AeroFS"))
                .body("folders[0].is_shared", equalTo(false))

                .body("folders[1].id", equalTo(object("d1").toStringFormal()))
                .body("folders[1].name", equalTo("d1"))
                .body("folders[1].is_shared", equalTo(false))
                .body("folders[1].parent", equalTo(id(mds.root().soid())))

                .body("folders[2].id", equalTo(object("d1/a2").toStringFormal()))
                .body("folders[2].name", equalTo("a2"))
                .body("folders[2].is_shared", equalTo(true))
                .body("folders[2].sid", equalTo(sid.toStringFormal()))
                .body("folders[2].parent", equalTo(object("d1").toStringFormal()))
        .when().log().everything()
                .get(RESOURCE + "/path", object("d1/a2/d3").toStringFormal());
    }

    @Test
    public void shouldReturnPathOnTS() throws Exception
    {
        Properties ts = new Properties();
        ts.setProperty("labeling.isMultiuser", "true");
        L.set(ts);

        try {
            assertTrue(L.isMultiuser());
            shouldReturnPath();
        } finally {
            L.set(new Properties());
        }
    }

    @Test
    public void shouldReturn404ForInvalidPath() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().log().everything()
                .get(RESOURCE + "/path", new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldGetExpelledPath() throws Exception
    {
        mds.root().dir("d1")
                .dir("d2", true)
                    .dir("d3");

        givenAccess()
        .expect()
                .statusCode(200)
                .body("folders[0].id", equalTo(id(mds.root().soid())))
                .body("folders[0].name", equalTo("AeroFS"))
                .body("folders[0].is_shared", equalTo(false))

                .body("folders[1].id", equalTo(object("d1").toStringFormal()))
                .body("folders[1].name", equalTo("d1"))
                .body("folders[1].is_shared", equalTo(false))
                .body("folders[1].parent", equalTo(id(mds.root().soid())))
        .when().log().everything()
                .get(RESOURCE + "/path", object("d1/d2").toStringFormal());
    }

    @Test
    public void shouldListRoot() throws Exception
    {
        mds.root()
                .dir("d").parent()
                .file("f").parent()
                .anchor("a");

        givenAccess()
        .expect()
                .statusCode(200)
                .body("files", hasSize(1)).body("files.name", hasItems("f"))
                .body("folders", hasSize(2)).body("folders.name", hasItems("d", "a"))
        .when().log().everything()
                .get(RESOURCE + "/children", "root");
    }

    @Test
    public void shouldListChildren() throws Exception
    {
        mds.root().dir("d0")
                .dir("d").parent()
                .file("f").parent()
                .anchor("a");

        givenAccess()
        .expect()
                .statusCode(200)
                .body("files", hasSize(1)).body("files.name", hasItems("f"))
                .body("folders", hasSize(2)).body("folders.name", hasItems("d", "a"))
        .when().log().everything()
                .get(RESOURCE + "/children", object("d0").toStringFormal());
    }

    @Test
    public void shouldListChildrenForSID() throws Exception
    {
        mds.root().dir("d0")
                .anchor("a")
                        .dir("d").parent()
                        .file("f").parent();
        SID sid = SID.anchorOID2storeSID(mds.root().dir("d0").anchor("a").soid().oid());

        givenAccess()
        .expect()
                .statusCode(200)
                .body("files", hasSize(1)).body("files.name", hasItems("f"))
                .body("folders", hasSize(1)).body("folders.name", hasItems("d"))
        .when().log().everything()
                .get(RESOURCE + "/children", sid.toStringFormal());
    }

    @Test
    public void shouldReturn404WhenListNonExistingDir() throws Exception
    {
        givenAccess()
                .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .get(RESOURCE + "/children",
                        new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldCreate() throws Exception
    {
        SettableFuture<SOID> soid = whenCreate(Type.DIR, "", "foo");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "foo")))
        .expect()
                .statusCode(201)
                .body("id", equalToFutureObject(soid))
                .body("name", equalTo("foo"))
                .body("is_shared", equalTo(false))
        .when().log().everything()
                .post("/v0.10/folders");
    }

    @Test
    public void shouldCreateUnderRoot() throws Exception
    {
        SettableFuture<SOID> soid = whenCreate(Type.DIR, "", "foo");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child("root", "foo")))
        .expect()
                .statusCode(201)
                .body("id", equalToFutureObject(soid))
                .body("name", equalTo("foo"))
                .body("is_shared", equalTo(false))
        .when().log().everything()
                .post("/v0.10/folders");
    }

    @Test
    public void shouldCreateUnderAnchor() throws Exception
    {
        mds.root().anchor("shared");

        SettableFuture<SOID> soid = whenCreate(Type.DIR, "shared", "foo");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(CommonMetadata.child(object("shared").toStringFormal(), "foo"),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(201)
                .body("id", equalToFutureObject(soid))
                .body("name", equalTo("foo"))
                .body("is_shared", equalTo(false))
        .when().log().everything()
                .post("/v0.10/folders");
    }

    @Test
    public void shouldReturn403WhenViewerTriesToCreate() throws Exception
    {
        doThrow(new ExNoPerm()).when(acl).checkThrows_(user, mds.root().soid().sidx(),
                Permissions.EDITOR);

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "foo")))
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .post("/v0.10/folders");
    }

    @Test
    public void shouldReturn403WhenTriesToCreateWithReadOnlyToken() throws Exception
    {
        givenReadAccess()
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
        givenAccess()
                .contentType(ContentType.JSON)
                .body(CommonMetadata.child(new RestObject(rootSID, OID.generate()).toStringFormal(),
                                "foo"),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .post("/v0.10/folders");
    }

    @Test
    public void shouldReturn400WhenTryingToCreateUnderFile() throws Exception
    {
        mds.root().file("f1");

        when(oc.create_(any(Type.class), any(SOID.class), anyString(), eq(PhysicalOp.APPLY), eq(t)))
                .thenThrow(new ExNotDir());

        givenAccess()
                .contentType(ContentType.JSON)
                .body(CommonMetadata.child(object("f1").toStringFormal(), "foo"),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .post("/v0.10/folders");
    }

    @Test
    public void shouldReturn409WhenAlreadyExists() throws Exception
    {
        whenCreate(Type.DIR, "", "foo");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "foo")))
        .expect()
                .statusCode(201)
        .when()
                .post("/v0.10/folders");

        givenAccess()
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
        SOID soid = mds.root().dir("foo").soid();
        SOID newParent = mds.root().dir("boo").soid();

        final String newFolderName = "moo";
        SettableFuture<SOID> newObjectId = whenMove("foo", "boo", newFolderName);

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, newParent.oid()).toStringFormal(), newFolderName)))
        .expect()
                .statusCode(200)
                .body("id", equalToFutureObject(newObjectId))
                .body("name", equalTo(newFolderName))
        .when()
                .put("/v0.10/folders/" + new RestObject(rootSID, soid.oid()).toStringFormal());

        assertEquals("Object id has changed", soid, newObjectId.get());
    }

    @Test
    public void shouldMoveFolderUnderAnchor() throws Exception
    {
        SOID soid = mds.root().dir("foo").soid();
        mds.root().anchor("boo");

        final String newFolderName = "moo";
        SettableFuture<SOID> newObjectId = whenMove("foo", "boo", newFolderName);

        givenAccess()
                .contentType(ContentType.JSON)
                .body(CommonMetadata.child(object("boo").toStringFormal(), newFolderName),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(200)
                .body("id", equalToFutureObject(newObjectId))
                .body("name", equalTo(newFolderName))
        .when()
                .put("/v0.10/folders/" + new RestObject(rootSID, soid.oid()).toStringFormal());

        assertNotEquals("store id hasn't changed", soid.sidx(), newObjectId.get().sidx());
        assertEquals("Object id has changed", soid.oid(), newObjectId.get().oid());
    }

    @Test
    public void shouldMoveFolderWhenEtagMatch() throws Exception
    {
        SOID soid = mds.root().dir("foo").soid();
        SOID newParent = mds.root().dir("boo").soid();

        final String newFolderName = "moo";
        SettableFuture<SOID> newObjectId = whenMove("foo", "boo", newFolderName);

        givenAccess()
                .contentType(ContentType.JSON)
                .header(Names.IF_MATCH, etagForMeta(soid))
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, newParent.oid()).toStringFormal(), newFolderName)))
        .expect()
                .statusCode(200)
                .body("id", equalToFutureObject(newObjectId))
                .body("name", equalTo(newFolderName))
        .when()
                .put("/v0.10/folders/" + new RestObject(rootSID, soid.oid()).toStringFormal());

        assertEquals("Object id has changed", soid, newObjectId.get());
    }

    @Test
    public void shouldReturn409WhenMoveConflict() throws Exception
    {
        SOID soid = mds.root().dir("foo").soid();

        String newFolderName = "boo";
        mds.root().dir(newFolderName);

        whenMove("foo", "", newFolderName);

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), newFolderName)))
        .expect()
                .statusCode(409)
                .body("type", equalTo(Error.Type.CONFLICT.toString()))
        .when()
                .put("/v0.10/folders/" + new RestObject(rootSID, soid.oid()).toStringFormal());
    }

    @Test
    public void shouldReturn412WhenMovingAndEtagChanged() throws Exception
    {
        SOID soid = mds.root().dir("foo").soid();

        givenAccess()
                .contentType(ContentType.JSON)
                .header(Names.IF_MATCH, OTHER_ETAG)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "bar")))
        .expect()
                .statusCode(412)
                .body("type", equalTo(Error.Type.CONFLICT.toString()))
        .when()
                .put("/v0.10/folders/" + new RestObject(rootSID, soid.oid()).toStringFormal());
    }

    @Test
    public void shouldReturn404MovingNonExistingDir() throws Exception
    {
        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "test")))
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .put("/v0.10/folders/" + new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn404MovingToNonExistingParent() throws Exception
    {
        SOID soid = mds.root().dir("foo").soid();

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, OID.generate()).toStringFormal(),
                        "test")))
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .put("/v0.10/folders/" + new RestObject(rootSID, soid.oid()).toStringFormal());
    }

    @Test
    public void shouldReturn400MovingUnderSelf() throws Exception
    {
        SOID soid = mds.root()
                .dir("foo").soid();

        givenAccess()
                .contentType(ContentType.JSON)
                .body(CommonMetadata.child(id(soid), "test"), ObjectMapperType.GSON)
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when().log().everything()
                .put("/v0.10/folders/" + id(soid));
    }

    @Test
    public void shouldReturn400MovingUnderOwnChild() throws Exception
    {
        SOID soid = mds.root()
                .dir("foo").soid();
        SOID child = mds.root()
                .dir("foo").dir("bar").soid();

        givenAccess()
                .contentType(ContentType.JSON)
                .body(CommonMetadata.child(id(child), "test"), ObjectMapperType.GSON)
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put("/v0.10/folders/" + id(soid));
    }

    @Test
    public void shouldReturn400MovingRoot() throws Exception
    {
        SOID soid = mds.root()
                .anchor("shared").root().soid();
        SOID child = mds.root()
                .dir("foo").soid();

        givenAccess()
                .contentType(ContentType.JSON)
                .body(CommonMetadata.child(id(child), "test"), ObjectMapperType.GSON)
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
                .body("message", equalTo("Invalid parameter: cannot move system folder"))
        .when()
                .put("/v0.10/folders/" + id(soid));
    }

    @Test
    public void shouldReturn400MovingUnderFile() throws Exception
    {
        SOID soid = mds.root()
                .file("bar").parent()
                .dir("foo").soid();

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("bar").toStringFormal(), "test")))
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put("/v0.10/folders/" + new RestObject(rootSID, soid.oid()).toStringFormal());
    }

    @Test
    public void shouldReturn403WhenViewerTriesToMove() throws Exception
    {
        SOID soid = mds.root().dir("foo").soid();

        doThrow(new ExNoPerm()).when(acl).checkThrows_(user, soid.sidx(), Permissions.EDITOR);

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "moo")))
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .put("/v0.10/folders/" + new RestObject(rootSID, soid.oid()).toStringFormal());
    }

    @Test
    public void shouldReturn403WhenTriesToMoveWithreadOnlyToken() throws Exception
    {
        SOID soid = mds.root().dir("foo").soid();

        givenReadAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "moo")))
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .put("/v0.10/folders/" + new RestObject(rootSID, soid.oid()).toStringFormal());
    }

    @Test
    public void shouldReturn204ForDeleteSuccess() throws Exception
    {
        SOID soid = mds.root().dir("foo").soid();

        givenAccess()
        .expect()
                .statusCode(204)
        .when()
                .delete("/v0.10/folders/" + new RestObject(rootSID, soid.oid()).toStringFormal());
    }

    @Test
    public void shouldReturn204WhenDeletingAndEtagMatch() throws Exception
    {
        SOID soid = mds.root().dir("foo").soid();

        givenAccess()
                .header(Names.IF_MATCH, etagForMeta(soid))
        .expect()
                .statusCode(204)
        .when()
                .delete("/v0.10/folders/" + new RestObject(rootSID, soid.oid()).toStringFormal());
    }

    @Test
    public void shouldReturn412WhenDeletingAndEtagChanged() throws Exception
    {
        SOID soid = mds.root().dir("foo").soid();

        givenAccess()
                .header(Names.IF_MATCH, OTHER_ETAG)
        .expect()
                .statusCode(412)
        .when()
                .delete("/v0.10/folders/" + new RestObject(rootSID, soid.oid()).toStringFormal());
    }

    @Test
    public void shouldReturn404ForDeleteNonExistent() throws Exception
    {
        givenAccess()
        .expect()
            .statusCode(404)
        .when()
            .delete("/v0.10/folders/" + new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn403WhenViewerTriesToDelete() throws Exception
    {
        SOID soid = mds.root().dir("foo").soid();

        doThrow(new ExNoPerm()).when(acl).checkThrows_(user, soid.sidx(), Permissions.EDITOR);

        givenAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .delete("/v0.10/folders/" + new RestObject(rootSID, soid.oid()).toStringFormal());
    }

    @Test
    public void shouldReturn403WhenTriesToDeleteWithReadOnlyToken() throws Exception
    {
        SOID soid = mds.root().dir("foo").soid();

        givenReadAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .delete("/v0.10/folders/" + new RestObject(rootSID, soid.oid()).toStringFormal());
    }

    @Test
    public void shouldReturn403WhenGetMetadataForAnchorWithNoPermWithLinkShareToken() throws Exception
    {
        mds.root()
            .dir("d0")
                .anchor("a")
                    .dir("d").parent()
                    .file("f").parent();

        SIndex sidxRoot = mds.root().soid().sidx();
        SIndex sidxChild = mds.root().dir("d0").anchor("a").dir("d").soid().sidx();
        when(acl.check_(any(UserID.class), eq(sidxRoot), any(Permissions.class))).thenReturn(true);
        when(acl.check_(any(UserID.class), eq(sidxChild), any(Permissions.class))).thenReturn(false);

        givenLinkShareReadAccess()
        .expect()
                .statusCode(403)
        .when().log().everything()
                .get(RESOURCE, object("d0/a").toStringFormal());
    }

    @Test
    public void shouldReturn403WhenGetMetadataForStoreWithNoPermWithLinkShareToken() throws Exception
    {
        mds.root()
            .dir("d0")
                .anchor("a")
                    .dir("d").parent()
                    .file("f").parent();

        SIndex sidxRoot = mds.root().soid().sidx();
        SIndex sidxChild = mds.root().dir("d0").anchor("a").dir("d").soid().sidx();
        when(acl.check_(any(UserID.class), eq(sidxRoot), any(Permissions.class))).thenReturn(true);
        when(acl.check_(any(UserID.class), eq(sidxChild), any(Permissions.class))).thenReturn(false);
        SID sid = SID.anchorOID2storeSID(mds.root().dir("d0").anchor("a").soid().oid());

        givenLinkShareReadAccess()
                .expect()
                .statusCode(403)
                .when().log().everything()
                .get(RESOURCE, new RestObject(sid, OID.ROOT).toStringFormal());
    }

    @Test
    public void shouldReturn200WhenGetMetadataForAnchorWithPermWithLinkShareToken() throws Exception
    {
        mds.root()
            .dir("d0")
                .anchor("a")
                    .dir("d").parent()
                    .file("f").parent();

        SIndex sidxRoot = mds.root().soid().sidx();
        SIndex sidxChild = mds.root().dir("d0").anchor("a").dir("d").soid().sidx();
        when(acl.check_(any(UserID.class), eq(sidxRoot), any(Permissions.class))).thenReturn(true);
        when(acl.check_(any(UserID.class), eq(sidxChild), any(Permissions.class))).thenReturn(true);

        givenLinkShareReadAccess()
        .expect()
                .statusCode(200)
                .body("name", equalTo("a"))
        .when().log().everything()
                .get(RESOURCE, object("d0/a").toStringFormal());
    }

    @Test
    public void shouldReturn200WhenGetMetadataForStoreWithPermWithLinkShareToken() throws Exception
    {
        mds.root()
            .dir("d0")
                .anchor("a")
                    .dir("d").parent()
                    .file("f").parent();

        SIndex sidxRoot = mds.root().soid().sidx();
        SIndex sidxChild = mds.root().dir("d0").anchor("a").dir("d").soid().sidx();
        when(acl.check_(any(UserID.class), eq(sidxRoot), any(Permissions.class))).thenReturn(true);
        when(acl.check_(any(UserID.class), eq(sidxChild), any(Permissions.class))).thenReturn(true);
        SID sid = SID.anchorOID2storeSID(mds.root().dir("d0").anchor("a").soid().oid());

        givenLinkShareReadAccess()
        .expect()
                .statusCode(200)
                .body("name", equalTo("a"))
        .when().log().everything()
                .get(RESOURCE, new RestObject(sid, OID.ROOT).toStringFormal());
    }

    @Test
    public void shouldHideChildAnchorWithNoPermWithLinkShareToken() throws Exception
    {
        mds.root()
            .dir("d0")
                .anchor("a")
                    .dir("d").parent()
                    .file("f").parent();

        SIndex sidxRoot = mds.root().soid().sidx();
        SIndex sidxChild = mds.root().dir("d0").anchor("a").dir("d").soid().sidx();
        when(acl.check_(any(UserID.class), eq(sidxRoot), any(Permissions.class))).thenReturn(true);
        when(acl.check_(any(UserID.class), eq(sidxChild), any(Permissions.class))).thenReturn(false);

        givenLinkShareReadAccess()
                .queryParam("fields", "children")
        .expect()
                .statusCode(200)
                .body("name", equalTo("d0"))
                .body("children.folders", iterableWithSize(0))
        .when().log().everything()
                .get(RESOURCE, object("d0").toStringFormal());
    }

    @Test
    public void shouldIncludeChildAnchorWithPermWithLinkShareToken() throws Exception
    {
        mds.root()
            .dir("d0")
                .anchor("a")
                    .dir("d").parent()
                    .file("f").parent();

        SIndex sidxRoot = mds.root().soid().sidx();
        SIndex sidxChild = mds.root().dir("d0").anchor("a").dir("d").soid().sidx();
        when(acl.check_(any(UserID.class), eq(sidxRoot), any(Permissions.class))).thenReturn(true);
        when(acl.check_(any(UserID.class), eq(sidxChild), any(Permissions.class))).thenReturn(true);

        givenLinkShareReadAccess()
                .queryParam("fields", "children")
        .expect()
                .statusCode(200)
                .body("name", equalTo("d0"))
                .body("children.folders", iterableWithSize(1))
                .body("children.folders[0].id", equalTo(object("d0/a").toStringFormal()))
                .body("children.folders[0].name", equalTo("a"))
        .when().log().everything()
                .get(RESOURCE, object("d0").toStringFormal());
    }
}
