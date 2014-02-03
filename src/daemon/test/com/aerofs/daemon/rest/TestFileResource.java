package com.aerofs.daemon.rest;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.mock.logical.MockDS.MockDSDir;
import com.aerofs.daemon.core.mock.logical.MockDS.MockDSFile;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.rest.util.RestObject;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.rest.api.CommonMetadata;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.SettableFuture;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isEmptyString;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestFileResource extends AbstractRestTest
{
    private final String RESOURCE = "/v0.9/files/{file}";

    private static long FILE_MTIME = 0xdeadbeef;
    private static byte[] FILE_CONTENT = { 'H', 'e', 'l', 'l', 'o'};


    public TestFileResource(boolean useProxy)
    {
        super(useProxy);
    }

    void mockContent(String path, final byte[] content) throws Exception
    {
        mds.root().file(path).caMaster(content.length, FILE_MTIME);
        IPhysicalFile pf = mock(IPhysicalFile.class);
        when(pf.newInputStream_()).thenAnswer(new Answer<InputStream>() {
            @Override
            public InputStream answer(InvocationOnMock invocation) throws Throwable
            {
                return new ByteArrayInputStream(content);
            }
        });
        when(ps.newFile_(eq(ds.resolve_(ds.resolveThrows_(Path.fromString(rootSID, path)))),
                eq(KIndex.MASTER)))
                .thenReturn(pf);
    }

    @Before
    public void mockFile() throws Exception
    {
        mockContent("f1", FILE_CONTENT);
        mockContent("f1.txt", FILE_CONTENT);
    }

    @Test
    public void shouldReturn400ForInvalidId() throws Exception
    {
        givenAcces()
        .expect()
                .statusCode(400)
                .header("Access-Control-Allow-Origin", "*")
                .body("type", equalTo("BAD_ARGS"))
        .when().get(RESOURCE, "bla");
    }

    @Test
    public void shouldReturn404ForNonExistingStore() throws Exception
    {
        givenAcces()
        .expect()
                .statusCode(404)
                .header("Access-Control-Allow-Origin", "*")
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, new RestObject(SID.generate(), OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn404ForNonExistingDir() throws Exception
    {
        givenAcces()
        .expect()
                .statusCode(404)
                .header("Access-Control-Allow-Origin", "*")
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn404ForDir() throws Exception
    {
        mds.root().dir("d0");

        givenAcces()
        .expect()
                .statusCode(404)
                .header("Access-Control-Allow-Origin", "*")
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, object("d0").toStringFormal());
    }

    @Test
    public void shouldGetMetadata() throws Exception
    {
        givenAcces()
        .expect()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", "*")
                .body("id", equalTo(object("f1").toStringFormal()))
                .body("name", equalTo("f1"))
                .body("size", equalTo(FILE_CONTENT.length))
                .body("last_modified", equalTo(ISO_8601.format(new Date(FILE_MTIME))))
                .body("mime_type", equalTo("application/octet-stream"))
        .when().get(RESOURCE, object("f1").toStringFormal());
    }

    @Test
    public void shouldGetMetadataWithMIME() throws Exception
    {
        givenAcces()
        .expect()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", "*")
                .body("id", equalTo(object("f1.txt").toStringFormal()))
                .body("name", equalTo("f1.txt"))
                .body("size", equalTo(FILE_CONTENT.length))
                .body("last_modified", equalTo(ISO_8601.format(new Date(FILE_MTIME))))
                .body("mime_type", equalTo("text/plain"))
        .when().get(RESOURCE, object("f1.txt").toStringFormal());
    }

    @Test
    public void shouldGet406IfNotAcceptingOctetStream() throws Exception
    {
        givenAcces()
                .header("Accept", "application/json")
        .expect()
                .statusCode(406)
                .header("Access-Control-Allow-Origin", "*")
        .when().get(RESOURCE + "/content", object("f1").toStringFormal());
    }

    @Test
    public void shouldGetContent() throws Exception
    {
        byte[] content =
        givenAcces()
                .header("Accept", "*/*")
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Length", Integer.toString(FILE_CONTENT.length))
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGetContentWithMIME() throws Exception
    {
        byte[] content =
        givenAcces()
                .header("Accept", "*/*")
        .expect()
                .statusCode(200)
                .contentType("text/plain")
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Length", Integer.toString(FILE_CONTENT.length))
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .when().get(RESOURCE + "/content", object("f1.txt").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGet304WhenEtagUnchanged() throws Exception
    {
        givenAcces()
                .header("Accept", "*/*")
                .header("If-None-Match", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .expect()
                .statusCode(304)
                .header("Access-Control-Allow-Origin", "*")
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
                .content(isEmptyString())
        .when().get(RESOURCE + "/content", object("f1").toStringFormal());
    }

    @Test
    public void shouldGetContentWhenEtagChanged() throws Exception
    {
        byte[] content =
        givenAcces()
                .header("Accept", "*/*")
                .header("If-None-Match", "\"f00\"")
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Length", Integer.toString(FILE_CONTENT.length))
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGetContentWhenEtagInvalid() throws Exception
    {
        byte[] content =
        givenAcces()
                .header("Accept", "*/*")
                .header("If-None-Match", "lowut")
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Length", Integer.toString(FILE_CONTENT.length))
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGetRangeWhenIfRangeMatchEtag() throws Exception
    {
        byte[] content =
        givenAcces()
                .header("Accept", "*/*")
                .header("Range", "bytes=0-1")
                .header("If-Range", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .expect()
                .statusCode(206)
                .contentType("application/octet-stream")
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Range", "bytes 0-1/" + FILE_CONTENT.length)
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(Arrays.copyOfRange(FILE_CONTENT, 0, 2), content);
    }

    @Test
    public void shouldGetFullContentWhenIfRangeMismatchEtag() throws Exception
    {
        byte[] content =
        givenAcces()
                .header("Accept", "*/*")
                .header("Range", "bytes=0-1")
                .header("If-Range", "\"f00\"")
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Length", Integer.toString(FILE_CONTENT.length))
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGetFullContentWhenIfRangeInvalid() throws Exception
    {
        // RFC 2616 does not specifically address this case so a safe
        // behavior was chosen
        byte[] content =
        givenAcces()
                .header("Accept", "*/*")
                .header("Range", "bytes=0-1")
                .header("If-Range", "lolwut")
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Access-Control-Allow-Origin", "*")
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGetFullContentWhenRangeInvalid() throws Exception
    {
        byte[] content =
        givenAcces()
                .header("Accept", "*/*")
                .header("Range", "bytes=1-0")
                .header("If-Range", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Length", Integer.toString(FILE_CONTENT.length))
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldReturn416WhenRangeNotSatisfiable() throws Exception
    {
        givenAcces()
                .header("Accept", "*/*")
                .header("Range", "bytes=" + (FILE_CONTENT.length) + "-")
        .expect()
                .statusCode(416)
                .content(isEmptyString())
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Range", "bytes */" + String.valueOf(FILE_CONTENT.length))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal());
    }

    @Test
    public void shouldGetFullContentWhenRangeEncompassing() throws Exception
    {
        byte[] content =
        givenAcces()
                .header("Accept", "*/*")
                .header("Range", "bytes=0-2,3-")
                .header("If-Range", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .expect()
                .statusCode(206)
                .contentType("application/octet-stream")
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Range", "bytes 0-" + String.valueOf(FILE_CONTENT.length - 1)
                        + "/" + String.valueOf(FILE_CONTENT.length))
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGetMultipartWhenDisjointSubrangesRequested() throws Exception
    {
        Response r =
        givenAcces()
                .header("Accept", "*/*")
                .header("Range", "bytes=0-1,3-")
                .header("If-Range", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .expect()
                .statusCode(206)
                .contentType("multipart/byteranges")
                .header("Access-Control-Allow-Origin", "*")
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal());

        MimeMultipart m = new MimeMultipart(new ByteArrayDataSource(r.getBody().asByteArray(),
                r.getContentType()));

        Assert.assertEquals(2, m.getCount());

        Assert.assertEquals("application/octet-stream", m.getBodyPart(0).getContentType());
        Assert.assertArrayEquals(Arrays.copyOfRange(FILE_CONTENT, 0, 2),
                ByteStreams.toByteArray(m.getBodyPart(0).getInputStream()));

        Assert.assertEquals("application/octet-stream", m.getBodyPart(1).getContentType());
        Assert.assertArrayEquals(Arrays.copyOfRange(FILE_CONTENT, 3, FILE_CONTENT.length),
                ByteStreams.toByteArray(m.getBodyPart(1).getInputStream()));
    }

    @Test
    public void shouldCreateFile() throws Exception
    {
        SettableFuture<SOID> soid = whenCreate(Type.FILE, "", "foo.txt");

        givenAcces()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "foo.txt")))
        .expect()
                .statusCode(201)
                .body("id", equalToFutureObject(soid))
                .body("name", equalTo("foo.txt"))
        .when().post("/v0.10/files");
    }

    @Test
    public void shouldReturn409WhenAlreadyExists() throws Exception
    {
        whenCreate(Type.FILE, "", "foo.txt");

        givenAcces()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "foo.txt")))
        .expect()
                .statusCode(201)
        .when().post("/v0.10/files");

        givenAcces()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "foo.txt")))
        .expect()
                .statusCode(409)
                .body("type", equalTo(com.aerofs.rest.api.Error.Type.CONFLICT.toString()))
        .when().post("/v0.10/files");
    }

    @Test
    public void shouldReturn403WhenViewerTriesToCreate() throws Exception
    {
        doThrow(new ExNoPerm()).when(acl).checkThrows_(
                user, mds.root().soid().sidx(), Permissions.EDITOR);
        givenAcces()
            .contentType(ContentType.JSON)
            .body(json(CommonMetadata.child(object("").toStringFormal(), "foo.txt")))
        .expect()
            .statusCode(403)
            .body("type", equalTo("FORBIDDEN"))
        .when()
            .post("/v0.10/files");
    }

    @Test
    public void shouldMoveFile() throws Exception
    {
        // create file
        MockDSFile file = mds.root().file("foo.txt");
        SOID fileId = file.soid();

        // create folder
        MockDSDir dir = mds.root().dir("myFolder");
        SOID folderId = dir.soid();

        // move file into folder
        String fileIdStr = new RestObject(rootSID, fileId.oid()).toStringFormal();
        final String newFileName = "foo1.txt";
        SettableFuture<SOID> newFileId = whenMove("foo.txt", "myFolder", newFileName);
        givenAcces()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, folderId.oid()).toStringFormal(),
                        newFileName)))
        .expect()
                .statusCode(200)
                .body("id", equalToFutureObject(newFileId))
                .body("name", equalTo(newFileName))
        .when()
            .put("/v0.10/files/" + fileIdStr);

        assertEquals("Object id has changed", fileId, newFileId.get());
    }

    @Test
    public void shouldReturn409WhenMoveConflict() throws Exception
    {
        // create first file
        MockDSFile file = mds.root().file("foo.txt");
        SOID fileId = file.soid();
        // create second file
        mds.root().file("boo.txt");
        String fileIdStr = new RestObject(rootSID, fileId.oid()).toStringFormal();
        whenMove("foo.txt", "", "boo.txt");
        givenAcces()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "boo.txt")))
        .expect()
                .statusCode(409)
                .body("type", equalTo(com.aerofs.rest.api.Error.Type.CONFLICT.toString()))
        .when()
                .put("/v0.10/files/" + fileIdStr);
    }

    @Test
    public void shouldReturn404MovingNonExistingFile() throws Exception
    {
        givenAcces()
            .contentType(ContentType.JSON)
            .body(json(CommonMetadata.child(object("").toStringFormal(), "test.txt")))
        .expect()
            .statusCode(404)
            .body("type", equalTo("NOT_FOUND"))
        .when()
            .put("/v0.10/files/" + new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn404MovingToNonExistingParrent() throws Exception
    {
        // create file
        MockDSFile file = mds.root().file("foo.txt");
        SOID fileId = file.soid();
        String fileIdStr = new RestObject(rootSID, fileId.oid()).toStringFormal();
        givenAcces()
            .contentType(ContentType.JSON)
            .body(json(CommonMetadata.child(new RestObject(rootSID,
                    OID.generate()).toStringFormal(), "test.txt")))
        .expect()
            .statusCode(404)
            .body("type", equalTo("NOT_FOUND"))
        .when()
            .put("/v0.10/files/" + fileIdStr);
    }

    @Test
    public void shouldReturn403WhenViewerTriesToMove() throws Exception
    {
        MockDSFile item = mds.root().file("foo.txt");
        SOID soid = item.soid();
        doThrow(new ExNoPerm()).when(acl).checkThrows_(
                user, soid.sidx(), Permissions.EDITOR);
        String idStr = new RestObject(rootSID, soid.oid()).toStringFormal();
        whenMove("foo.txt", "", "moo.txt");
        givenAcces()
            .contentType(ContentType.JSON)
            .body(json(CommonMetadata.child(object("").toStringFormal(), "moo.txt")))
        .expect()
            .statusCode(403)
            .body("type", equalTo("FORBIDDEN"))
        .when()
            .put("/v0.10/files/" + idStr);
    }

    @Test
    public void shouldReturn204ForDeleteSuccess() throws Exception
    {
        // create file
        MockDSFile file = mds.root().file("foo.txt");
        SOID soid = file.soid();
        String fileIdStr = new RestObject(rootSID, soid.oid()).toStringFormal();
        givenAcces()
        .expect()
            .statusCode(204)
        .when()
            .delete("/v0.10/files/" + fileIdStr);

    }

    @Test
    public void shouldReturn404ForDeleteNonExistent() throws Exception
    {
        givenAcces()
        .expect()
            .statusCode(404)
        .when()
            .delete("/v0.10/files/" + new RestObject(rootSID, OID.generate()).toStringFormal());

    }

    @Test
    public void shouldReturn403WhenViewerTriesToDelete() throws Exception
    {
        doThrow(new ExNoPerm()).when(acl).checkThrows_(
                user, mds.root().soid().sidx(), Permissions.EDITOR);
        MockDSFile item = mds.root().file("foo.txt");
        SOID soid = item.soid();
        String idStr = new RestObject(rootSID, soid.oid()).toStringFormal();
        doThrow(new ExNoPerm()).when(acl).checkThrows_(
                user, soid.sidx(), Permissions.EDITOR);
        givenAcces()
            .expect()
            .statusCode(403)
            .body("type", equalTo("FORBIDDEN"))
        .when()
            .delete("/v0.10/files/" + idStr);
    }

    @Test
    public void shouldUploadContent() throws Exception
    {
        MockDSFile file = mds.root().file("foo.txt");
        SOID fileId = file.soid();
        String fileIdStr = new RestObject(rootSID, fileId.oid()).toStringFormal();
        byte[] content = BaseUtil.string2utf("Sample file content");
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(pf.newOutputStream_(anyBoolean())).thenReturn(baos);
        givenAcces()
            .content(content)
        .expect()
            .statusCode(200)
        .when()
            .put("/v0.10/files/" + fileIdStr + "/content");
        assertArrayEquals("Output Does Not match", content, baos.toByteArray());
    }

    @Test
    public void shouldReturn404WhenUploadToNonExistingFile() throws Exception
    {
        String fileIdStr = new RestObject(rootSID, OID.generate()).toStringFormal();
        byte[] content = BaseUtil.string2utf("Sample file content");
        givenAcces()
            .content(content)
        .expect()
            .statusCode(404)
        .when()
            .put("/v0.10/files/" + fileIdStr + "/content");
    }

    @Test
    public void shouldReturn403WhenViewerTriesToUpload() throws Exception
    {
        doThrow(new ExNoPerm()).when(acl).checkThrows_(
                user, mds.root().soid().sidx(), Permissions.EDITOR);
        MockDSFile file = mds.root().file("foo.txt");
        SOID fileId = file.soid();
        String fileIdStr = new RestObject(rootSID, fileId.oid()).toStringFormal();
        byte[] content = BaseUtil.string2utf("Sample file content");
        givenAcces()
            .content(content)
        .expect()
            .statusCode(403)
        .when()
            .put("/v0.10/files/" + fileIdStr + "/content");
    }


    @Test
    public void shouldReturn429WhenOutOfUploadTokens() throws Exception
    {
        doThrow(new ExNoResource()).when(tokenManager).acquireThrows_(eq(Cat.CLIENT), anyString());

        SOID soid = mds.root().file("foo.txt").soid();

        givenAcces()
                .content(FILE_CONTENT)
        .expect()
                .statusCode(429)
                .body("type", equalTo("TOO_MANY_REQUESTS"))
        .when()
                .put("/v0.10/files/" + new RestObject(rootSID, soid.oid()).toStringFormal()
                        + "/content");
    }
}
