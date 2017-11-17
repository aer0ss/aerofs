package com.aerofs.daemon.rest;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.RestObject;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.protocol.SendableContent;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UploadID;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.rest.api.CommonMetadata;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.SettableFuture;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.internal.mapper.ObjectMapperType;
import com.jayway.restassured.response.Response;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class TestFileResource extends AbstractRestTest
{
    private final String RESOURCE = "/v1.2/files/{file}";

    private static long FILE_MTIME = 0xdeadbeef;
    private static byte[] FILE_CONTENT = {'H', 'e', 'l', 'l', 'o'};

    public TestFileResource(boolean useProxy)
    {
        super(useProxy);
    }

    void mockFile(String path, final byte[] content) throws Exception
    {
        mds.root().file(path).caMaster(content.length, FILE_MTIME);
        mockContent(path, content);
    }

    void mockContent(String path, final byte[] content) throws Exception
    {
        IPhysicalFile pf = mock(IPhysicalFile.class);
        SOID soid = ds.resolveThrows_(Path.fromString(rootSID, path));

        when(pf.exists_()).thenReturn(true);
        when(pf.lastModified()).thenReturn(FILE_MTIME);
        when(pf.lengthOrZeroIfNotFile()).thenReturn((long)FILE_CONTENT.length);
        when(pf.newInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(content));
        when(ps.newFile_(eq(ds.resolve_(soid)), eq(KIndex.MASTER))).thenReturn(pf);

        when(provider.content(any(SOKID.class)))
                .thenReturn(new SendableContent(new SOKID(soid, KIndex.MASTER), FILE_MTIME,
                        FILE_CONTENT.length, new ContentHash(CONTENT_HASH) ,pf));
    }

    @Before
    public void mockFile() throws Exception
    {
        mockFile("f1", FILE_CONTENT);
        mockFile("f1.txt", FILE_CONTENT);
        mds.root().dir("expelled", true).file("f2");
        mds.root().file("f3", 0);
        mds.root().dir("d0").anchor("a0").file("f0").caMaster(FILE_CONTENT.length, FILE_MTIME);
        mockContent("d0/a0/f0", FILE_CONTENT);
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
        .when().get(RESOURCE, new RestObject(SID.generate(), OID.generate()).toStringFormal());
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
    public void shouldReturn404ForDir() throws Exception
    {
        mds.root().dir("d0");

        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, object("d0").toStringFormal());
    }

    @Test
    public void shouldGetMetadata() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(object("f1").toStringFormal()))
                .body("name", equalTo("f1"))
                .body("size", equalTo(FILE_CONTENT.length))
                .body("last_modified", equalTo(ISO_8601.format(new Date(FILE_MTIME))))
                .body("mime_type", equalTo("application/octet-stream"))
                .body("etag", equalTo(CURRENT_ETAG_VALUE))
                .body("content_state", equalTo("AVAILABLE"))
        .when().get(RESOURCE, object("f1").toStringFormal());
    }

    @Test
    public void shouldGetMetadataForExpelled() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(object("expelled/f2").toStringFormal()))
                .body("name", equalTo("f2"))
                .body("size", nullValue())
                .body("last_modified", nullValue())
                .body("mime_type", equalTo("application/octet-stream"))
                .body("etag", equalTo(CURRENT_ETAG_VALUE))
                .body("content_state", equalTo("DESELECTED"))
        .when().get(RESOURCE, object("expelled/f2").toStringFormal());
    }

    @Test
    public void shouldGetMetadataForSyncing() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(object("f3").toStringFormal()))
                .body("name", equalTo("f3"))
                .body("size", nullValue())
                .body("last_modified", nullValue())
                .body("mime_type", equalTo("application/octet-stream"))
                .body("etag", equalTo(CURRENT_ETAG_VALUE))
                .body("content_state", equalTo("SYNCING"))
        .when().get(RESOURCE, object("f3").toStringFormal());
    }

    @Test
    public void shouldGetMetadataForQuotaExceeded() throws Exception
    {
        when(csdb.isCollectingContent_(any(SIndex.class))).thenReturn(false);

        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(object("f3").toStringFormal()))
                .body("name", equalTo("f3"))
                .body("size", nullValue())
                .body("last_modified", nullValue())
                .body("mime_type", equalTo("application/octet-stream"))
                .body("etag", equalTo(CURRENT_ETAG_VALUE))
                .body("content_state", equalTo("INSUFFICIENT_STORAGE"))
        .when().get(RESOURCE, object("f3").toStringFormal());
    }

    @Test
    public void shouldGetMetadataWithOnDemandFields() throws Exception
    {
        mds.root().dir("d0").file("f1");

        givenAccess()
                .queryParam("fields", "path")
        .expect()
                .statusCode(200)
                .body("id", equalTo(object("d0/f1").toStringFormal()))
                .body("name", equalTo("f1"))
                .body("size", equalTo(0))
                .body("mime_type", equalTo("application/octet-stream"))
                .body("etag", equalTo(CURRENT_ETAG_VALUE))
                .body("content_state", equalTo("AVAILABLE"))
                .body("path.folders", iterableWithSize(2))
                .body("path.folders[0].name", equalTo("AeroFS"))
                .body("path.folders[0].id", equalTo(id(mds.root().soid())))
                .body("path.folders[0].is_shared", equalTo(false))
                .body("path.folders[1].name", equalTo("d0"))
                .body("path.folders[1].id", equalTo(object("d0").toStringFormal()))
                .body("path.folders[1].is_shared", equalTo(false))
        .when().log().everything()
                .get(RESOURCE, object("d0/f1").toStringFormal());
    }

    @Test
    public void shouldGetMetadataWithMIME() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(object("f1.txt").toStringFormal()))
                .body("name", equalTo("f1.txt"))
                .body("size", equalTo(FILE_CONTENT.length))
                .body("last_modified", equalTo(ISO_8601.format(new Date(FILE_MTIME))))
                .body("mime_type", equalTo("text/plain"))
                .body("etag", equalTo(CURRENT_ETAG_VALUE))
                .body("content_state", equalTo("AVAILABLE"))
        .when().get(RESOURCE, object("f1.txt").toStringFormal());
    }

    @Test
    public void shouldReturnPath() throws Exception
    {
        mds.root().dir("d1").anchor("a2").file("f3");

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
                .body("folders[2].parent", equalTo(object("d1").toStringFormal()))
        .when().log().everything()
                .get(RESOURCE + "/path", object("d1/a2/f3").toStringFormal());
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
    public void shouldReturnHeadersForHeadRequest() throws Exception
    {
        givenAccess()
                .header("Accept", "*/*")
        .expect()
                .statusCode(200)
                .header("Content-Type", "text/plain")
                .header("Content-Length", Integer.toString(FILE_CONTENT.length))
                .header("ETag", CURRENT_ETAG)
        .when().head(RESOURCE + "/content", object("f1.txt").toStringFormal());

        //Chrome
        givenAccess()
                .header("Accept", "*/*")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_5) AppleWebKit/" +
                        "537.36 (KHTML, like Gecko) Chrome/47.0.2526.106 Safari/537.36")
        .expect()
                .statusCode(200)
                .header("Content-Type", "text/plain")
                .header("Content-Length", Integer.toString(FILE_CONTENT.length))
                .header("ETag", CURRENT_ETAG)
        .when().head(RESOURCE + "/content", object("f1.txt").toStringFormal());

        //Safari
        givenAccess()
                .header("Accept", "*/*")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_5) AppleWebKit/" +
                        "601.3.9 (KHTML, like Gecko) Version/9.0.2 Safari/601.3.9")
        .expect()
                .statusCode(200)
                .header("Content-Type", "text/plain")
                .header("Content-Length", Integer.toString(FILE_CONTENT.length))
                .header("ETag", CURRENT_ETAG)
        .when().head(RESOURCE + "/content", object("f1.txt").toStringFormal());
    }

    @Test
    public void shouldNotReturnContentLengthForHeadRequestinIE() throws Exception
    {
        givenAccess()
                .header("Accept", "*/*")
                .header("User-Agent", "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.1; Trident/6.0)")
        .expect()
                .statusCode(200)
                .header("Content-Type", "text/plain")
                .header("Content-Length", equalTo(null))
                .header("ETag", CURRENT_ETAG)
        .when().head(RESOURCE + "/content", object("f1.txt").toStringFormal());

        givenAccess()
                .header("Accept", "*/*")
                .header("User-Agent", " Mozilla/4.0 (compatible; MSIE 7.0; Windows NT 6.1; Trident/6.0)")
        .expect()
                .statusCode(200)
                .header("Content-Type", "text/plain")
                .header("Content-Length", equalTo(null))
                .header("ETag", CURRENT_ETAG)
        .when().head(RESOURCE + "/content", object("f1.txt").toStringFormal());
    }

    @Test
    public void shouldNotReturnContentLengthForHeadRequestinIE11() throws Exception
    {
        givenAccess()
                .header("Accept", "*/*")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64; Trident/7.0; rv:11.0) like Gecko")
        .expect()
                .statusCode(200)
                .header("Content-Type", "text/plain")
                .header("Content-Length", equalTo(null))
                .header("ETag", CURRENT_ETAG)
        .when().head(RESOURCE + "/content", object("f1.txt").toStringFormal());
    }

    @Test
    public void shouldNotReturnContentLengthForHeadRequestinIEEdge() throws Exception
    {
        givenAccess()
                .header("Accept", "*/*")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/532.36 " +
                        "(KHTML, like Gecko) Chrome/42.0.2311.135 Safari/537.36 Edge/12.10240")
        .expect()
                .statusCode(200)
                .header("Content-Type", "text/plain")
                .header("Content-Length", equalTo(null))
                .header("ETag", CURRENT_ETAG)
        .when().head(RESOURCE + "/content", object("f1.txt").toStringFormal());
    }

    @Test
    public void shouldGet406IfNotAcceptingOctetStream() throws Exception
    {
        givenAccess()
                .header("Accept", "application/json")
        .expect()
                .statusCode(406)
        .when().get(RESOURCE + "/content", object("f1").toStringFormal());
    }

    @Test
    public void shouldReturn404ForDisappearingFile() throws Exception
    {
        mds.root().file("foobar").caMaster(12, FILE_MTIME);
        IPhysicalFile pf = mock(IPhysicalFile.class);
        when(pf.exists_()).thenReturn(false);
        when(pf.newInputStream()).thenThrow(new IOException());
        when(ps.newFile_(eq(ds.resolve_(ds.resolveThrows_(Path.fromString(rootSID, "foobar")))),
                eq(KIndex.MASTER)))
                .thenReturn(pf);

        when(provider.content(any(SOKID.class)))
                .thenReturn(new SendableContent(mock(SOKID.class), FILE_MTIME,
                        FILE_CONTENT.length, new ContentHash(CONTENT_HASH) ,pf));

        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE + "/content", object("foobar").toStringFormal());
    }

    @Test
    public void shouldGetContent() throws Exception
    {
        byte[] content =
        givenAccess()
                .header("Accept", "*/*")
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Content-Length", Integer.toString(FILE_CONTENT.length))
                .header("Etag", CURRENT_ETAG)
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGetContentThroughLinkSharing() throws Exception
    {
        when(acl.check_(eq(user), any(SIndex.class), any(Permissions.class))).thenReturn(true);

        byte[] content =
        givenLinkShareReadAccessTo(object("d0"))
                .header("Accept", "*/*")
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Content-Length", Integer.toString(FILE_CONTENT.length))
                .header("Etag", CURRENT_ETAG)
        .when().log().everything().get(RESOURCE + "/content", object("d0/a0/f0").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldReturn403WhenGetContentWithUnrelatedLinkSharingToken() throws Exception
    {
        when(acl.check_(eq(user), any(SIndex.class), any(Permissions.class))).thenReturn(true);

        givenLinkShareReadAccessTo(object("f1"))
                .header("Accept", "*/*")
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
                .body("message", equalTo("Token lacks required scope"))
        .when().log().everything().get(RESOURCE + "/content", object("d0/a0/f0").toStringFormal());
    }

    @Test
    public void shouldReturn403WhenGetContentThroughLinkWithoutManagePermission() throws Exception
    {
        givenLinkShareReadAccessTo(object("d0"))
                .header("Accept", "*/*")
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
                .body("message", equalTo("Token lacks required scope"))
        .when().get(RESOURCE + "/content", object("d0/a0/f0").toStringFormal());
    }

    @Test
    public void shouldReturn404WhenGetExpelledContent() throws Exception
    {
        givenAccess()
                .header("Accept", "*/*")
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("Content not synced on this device"))
        .when().get(RESOURCE + "/content", object("expelled/f2").toStringFormal());
    }

    @Test
    public void shouldReturn404WhenGetUnavailableContent() throws Exception
    {
        givenAccess()
                .header("Accept", "*/*")
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("Content not yet available on this device"))
        .when().get(RESOURCE + "/content", object("f3").toStringFormal());
    }

    @Test
    public void shouldReturn404WhenGetQuotaExceededContent() throws Exception
    {
        when(csdb.isCollectingContent_(any(SIndex.class))).thenReturn(false);

        givenAccess()
                .header("Accept", "*/*")
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("Quota exceeded"))
        .when().get(RESOURCE + "/content", object("f3").toStringFormal());
    }

    @Test
    public void shouldReturn401WhenGetContentWithoutToken() throws Exception
    {
        given()
                .header("Accept", "*/*")
        .expect()
                .statusCode(401)
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();
    }

    @Test
    public void shouldGetContentWithMIME() throws Exception
    {
        byte[] content =
        givenAccess()
                .header("Accept", "*/*")
        .expect()
                .statusCode(200)
                .contentType("text/plain")
                .header("Content-Length", Integer.toString(FILE_CONTENT.length))
                .header("Etag", CURRENT_ETAG)
        .when().get(RESOURCE + "/content", object("f1.txt").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGet304WhenEtagUnchanged() throws Exception
    {
        givenAccess()
                .header("Accept", "*/*")
                .header("If-None-Match", CURRENT_ETAG)
        .expect()
                .statusCode(304)
                .header("Etag", CURRENT_ETAG)
                .content(isEmptyString())
        .when().get(RESOURCE + "/content", object("f1").toStringFormal());
    }

    @Test
    public void shouldGetContentWhenEtagChanged() throws Exception
    {
        byte[] content =
        givenAccess()
                .header("Accept", "*/*")
                .header("If-None-Match", "\"f00\"")
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Content-Length", Integer.toString(FILE_CONTENT.length))
                .header("Etag", CURRENT_ETAG)
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGetContentWhenEtagInvalid() throws Exception
    {
        byte[] content =
        givenAccess()
                .header("Accept", "*/*")
                .header("If-None-Match", "lowut")
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Content-Length", Integer.toString(FILE_CONTENT.length))
                .header("Etag", CURRENT_ETAG)
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGetRangeWhenIfRangeMatchEtag() throws Exception
    {
        byte[] content =
        givenAccess()
                .header("Accept", "*/*")
                .header(Names.RANGE, "bytes=0-1")
                .header("If-Range", CURRENT_ETAG)
        .expect()
                .statusCode(206)
                .contentType("application/octet-stream")
                .header("Content-Range", "bytes 0-1/" + FILE_CONTENT.length)
                .header("Etag", CURRENT_ETAG)
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(Arrays.copyOfRange(FILE_CONTENT, 0, 2), content);
    }

    @Test
    public void shouldGetFullContentWhenIfRangeMismatchEtag() throws Exception
    {
        byte[] content =
        givenAccess()
                .header("Accept", "*/*")
                .header(Names.RANGE, "bytes=0-1")
                .header("If-Range", "\"f00\"")
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Content-Length", Integer.toString(FILE_CONTENT.length))
                .header("Etag", CURRENT_ETAG)
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
        givenAccess()
                .header("Accept", "*/*")
                .header(Names.RANGE, "bytes=0-1")
                .header("If-Range", "lolwut")
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Etag", CURRENT_ETAG)
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGetFullContentWhenRangeInvalid() throws Exception
    {
        byte[] content =
        givenAccess()
                .header("Accept", "*/*")
                .header(Names.RANGE, "bytes=1-0")
                .header("If-Range", CURRENT_ETAG)
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Content-Length", Integer.toString(FILE_CONTENT.length))
                .header("Etag", CURRENT_ETAG)
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldReturn416WhenRangeNotSatisfiable() throws Exception
    {
        givenAccess()
                .header("Accept", "*/*")
                .header(Names.RANGE, "bytes=" + (FILE_CONTENT.length) + "-")
        .expect()
                .statusCode(416)
                .content(isEmptyString())
                .header("Content-Range", "bytes */" + String.valueOf(FILE_CONTENT.length))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal());
    }

    @Test
    public void shouldGetFullContentWhenRangeEncompassing() throws Exception
    {
        byte[] content =
        givenAccess()
                .header("Accept", "*/*")
                .header(Names.RANGE, "bytes=0-2,3-")
                .header("If-Range", CURRENT_ETAG)
        .expect()
                .statusCode(206)
                .contentType("application/octet-stream")
                .header("Content-Range", "bytes 0-" + String.valueOf(FILE_CONTENT.length - 1)
                        + "/" + String.valueOf(FILE_CONTENT.length))
                .header("Etag", CURRENT_ETAG)
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGetMultipartWhenDisjointSubrangesRequested() throws Exception
    {
        Response r =
        givenAccess()
                .header("Accept", "*/*")
                .header(Names.RANGE, "bytes=0-1,3-")
                .header("If-Range", CURRENT_ETAG)
        .expect()
                .statusCode(206)
                .contentType("multipart/byteranges")
                .header("Etag", CURRENT_ETAG)
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
        SettableFuture<SOID> soid = whenCreate(OA.Type.FILE, "", "foo.txt");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "foo.txt")))
        .expect()
                .statusCode(201)
                .body("id", equalToFutureObject(soid))
                .body("name", equalTo("foo.txt"))
                .body("content_state", equalTo("SYNCING"))
                .body("size", nullValue())
                .body("last_modified", nullValue())
        .when().post("/v0.10/files");
    }

    @Test
    public void shouldCreateFileUnderRoot() throws Exception
    {
        SettableFuture<SOID> soid = whenCreate(OA.Type.FILE, "", "foo.txt");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child("root", "foo.txt")))
        .expect()
                .statusCode(201)
                .body("id", equalToFutureObject(soid))
                .body("name", equalTo("foo.txt"))
                .body("content_state", equalTo("SYNCING"))
                .body("size", nullValue())
                .body("last_modified", nullValue())
        .when().post("/v1.2/files");
    }

    @Test
    public void shouldCreateFileUnderAnchor() throws Exception
    {
        mds.root().anchor("shared");

        SettableFuture<SOID> soid = whenCreate(OA.Type.FILE, "shared", "foo.txt");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(CommonMetadata.child(object("shared").toStringFormal(), "foo.txt"),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(201)
                .body("id", equalToFutureObject(soid))
                .body("name", equalTo("foo.txt"))
                .body("content_state", equalTo("SYNCING"))
                .body("size", nullValue())
                .body("last_modified", nullValue())
        .when().post("/v0.10/files");
    }

    @Test
    public void shouldReturn409WhenAlreadyExists() throws Exception
    {
        whenCreate(OA.Type.FILE, "", "foo.txt");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child("root", "foo.txt")))
        .expect()
                .statusCode(201)
        .when().post("/v1.2/files");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "foo.txt")))
        .expect()
                .statusCode(409)
                .body("type", equalTo("CONFLICT"))
        .when().post("/v0.10/files");
    }

    @Test
    public void shouldReturn400IfNoFileObjectGiven() throws Exception
    {
        givenAccess()
                .contentType(ContentType.JSON)
                .body("")
        .expect()
                .statusCode(400)
        .when().post("/v0.10/files");
    }

    @Test
    public void shouldReturn404WhenCreatingUnderNonExistingParent() throws Exception
    {
        givenAccess()
                .contentType(ContentType.JSON)
                .body(CommonMetadata.child(new RestObject(rootSID, OID.generate()).toStringFormal(),
                                "foo"), ObjectMapperType.GSON)
        .expect()
                .statusCode(404)
        .when().post("/v0.10/files");
    }

    @Test
    public void shouldReturn400WhenCreatingUnderFile() throws Exception
    {
        mds.root().file("foo");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(CommonMetadata.child(
                        object("foo").toStringFormal(),
                        "bar"),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(400)
        .when().post("/v0.10/files");
    }

    @Test
    public void shouldReturn403WhenViewerTriesToCreate() throws Exception
    {
        doThrow(new ExNoPerm())
                .when(acl).checkThrows_(user, mds.root().soid().sidx(), Permissions.EDITOR);
        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "foo.txt")))
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .post("/v0.10/files");
    }

    @Test
    public void shouldReturn403WhenTriesToCreateWithReadOnlyToken() throws Exception
    {
        givenReadAccess()
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
        SOID soid = mds.root().file("foo.txt").soid();
        SOID newParent = mds.root().dir("myFolder").soid();

        final String newFileName = "foo1.txt";
        SettableFuture<SOID> newFileId = whenMove("foo.txt", "myFolder", newFileName);
        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(id(newParent.oid()), newFileName)))
        .expect()
                .statusCode(200)
                .body("id", equalToFutureObject(newFileId))
                .body("name", equalTo(newFileName))
        .when()
                .put("/v0.10/files/" + id(soid));

        assertEquals("Object id has changed", soid, newFileId.get());
    }

    @Test
    public void shouldMoveFileUnderAnchor() throws Exception
    {
        SOID soid = mds.root().dir("foo.txt").soid();
        SOID newParent = mds.root().anchor("myFolder").soid();

        final String newFileName = "foo1.txt";
        SettableFuture<SOID> newObjectId = whenMove("foo.txt", "myFolder", newFileName);

        givenAccess()
                .contentType(ContentType.JSON)
                .body(CommonMetadata.child(id(newParent.oid()), newFileName),
                        ObjectMapperType.GSON)
        .expect()
                .statusCode(200)
                .body("id", equalToFutureObject(newObjectId))
                .body("name", equalTo(newFileName))
        .when()
                .put("/v0.10/files/" + new RestObject(rootSID, soid.oid()).toStringFormal());

        assertNotEquals("store id hasn't changed", soid.sidx(), newObjectId.get().sidx());
        assertEquals("Object id has changed", soid.oid(), newObjectId.get().oid());
    }

    @Test
    public void shouldMoveFileWhenEtagMatch() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();
        SOID newParent = mds.root().dir("myFolder").soid();

        final String newFileName = "foo1.txt";
        SettableFuture<SOID> newFileId = whenMove("foo.txt", "myFolder", newFileName);
        givenAccess()
                .header(Names.IF_MATCH, etagForMeta(soid))
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(id(newParent), newFileName)))
        .expect()
                .statusCode(200)
                .body("id", equalToFutureObject(newFileId))
                .body("name", equalTo(newFileName))
        .when()
                .put("/v0.10/files/" + id(soid));

        assertEquals("Object id has changed", soid, newFileId.get());
    }

    @Test
    public void shouldReturn409WhenMoveConflict() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        mds.root().file("boo.txt");

        whenMove("foo.txt", "", "boo.txt");

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "boo.txt")))
        .expect()
                .statusCode(409)
                .body("type", equalTo("CONFLICT"))
        .when()
                .put("/v0.10/files/" + id(soid));
    }

    @Test
    public void shouldReturn412WhenMoveAndEtagChanged() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        givenAccess()
                .header(Names.IF_MATCH, OTHER_ETAG)
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "bar.txt")))
        .expect()
                .statusCode(412)
                .body("type", equalTo("CONFLICT"))
        .when()
                .put("/v0.10/files/" + id(soid));
    }

    @Test
    public void shouldReturn404MovingNonExistingFile() throws Exception
    {
        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "test.txt")))
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .put("/v0.10/files/" + id(OID.generate()));
    }

    @Test
    public void shouldReturn404MovingToNonExistingParent() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        new RestObject(rootSID, OID.generate()).toStringFormal(),
                        "test.txt")))
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when()
                .put("/v0.10/files/" + id(soid));
    }

    @Test
    public void shouldReturn400MovingUnderFile() throws Exception
    {
        SOID soid = mds.root()
                .file("bar").parent()
                .file("foo.txt").soid();

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(
                        object("bar").toStringFormal(),
                        "test")))
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when()
                .put("/v0.10/folders/" + new RestObject(rootSID, soid.oid()).toStringFormal());
    }

    @Test
    public void shouldReturn403WhenViewerTriesToMove() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        doThrow(new ExNoPerm()).when(acl).checkThrows_(user, soid.sidx(), Permissions.EDITOR);

        givenAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "moo.txt")))
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .put("/v0.10/files/" + id(soid));
    }

    @Test
    public void shouldReturn403WhenTriesToMoveWithReadOnlyToken() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        givenReadAccess()
                .contentType(ContentType.JSON)
                .body(json(CommonMetadata.child(object("").toStringFormal(), "moo.txt")))
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .put("/v0.10/files/" + id(soid));
    }

    @Test
    public void shouldReturn204WhenDeleting() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        givenAccess()
        .expect()
                .statusCode(204)
        .when()
                .delete("/v0.10/files/" + id(soid));

        verify(od).delete_(soid, PhysicalOp.APPLY, t);
    }

    @Test
    public void shouldReturn204WhenDeletingAndEtagMatch() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        givenAccess()
                .header(Names.IF_MATCH, etagForMeta(soid))
        .expect()
                .statusCode(204)
        .when()
                .delete("/v0.10/files/" + id(soid));

        verify(od).delete_(soid, PhysicalOp.APPLY, t);
    }

    @Test
    public void shouldReturn412WhenDeletingAndEtagChanged() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        givenAccess()
                .header(Names.IF_MATCH, OTHER_ETAG)
        .expect()
                .statusCode(412)
        .when()
                .delete("/v0.10/files/" + id(soid));

        verifyZeroInteractions(od);
    }

    @Test
    public void shouldReturn404WhenDeletingNonExistent() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
        .when()
                .delete("/v0.10/files/" + id(OID.generate()));

        verifyZeroInteractions(od);
    }

    @Test
    public void shouldReturn403WhenViewerTriesToDelete() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        doThrow(new ExNoPerm()).when(acl).checkThrows_(user, soid.sidx(), Permissions.EDITOR);

        givenAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .delete("/v0.10/files/" + id(soid));

        verifyZeroInteractions(od);
    }

    @Test
    public void shouldReturn403WhenTriesToDeleteWithReadOnlyToken() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        givenReadAccess()
        .expect()
                .statusCode(403)
                .body("type", equalTo("FORBIDDEN"))
        .when()
                .delete("/v0.10/files/" + id(soid));

        verifyZeroInteractions(od);
    }

    @Test
    public void shouldUploadContent() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        givenAccess()
                .content(FILE_CONTENT)
        .expect()
                .statusCode(204)
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");

        assertArrayEquals("Output Does Not match", FILE_CONTENT, pf.data());
    }

    @Test
    public void shouldReturn507WhenUploadContentAndOutOfQuota() throws Exception
    {
        when(csdb.isCollectingContent_(any(SIndex.class))).thenReturn(false);

        SOID soid = mds.root().file("foo.txt").soid();

        givenAccess()
                .content(FILE_CONTENT)
        .expect()
                .statusCode(507)
                .body("type", equalTo("INSUFFICIENT_STORAGE"))
                .body("message", equalTo("Quota exceeded"))
        .when().put("/v0.10/files/" + id(soid) + "/content");
    }

    @Test
    public void shouldReturn404WhenUploadContentAndExpelled() throws Exception
    {
        givenAccess()
                .content(FILE_CONTENT)
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
                .body("message", equalTo("Content not synced on this device"))
        .when().put("/v0.10/files/" + object("expelled/f2").toStringFormal() + "/content");
    }

    @Test
    public void shouldUploadContentWhenEtagMatch() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        givenAccess()
                .header(Names.IF_MATCH, CURRENT_ETAG)
                .content(FILE_CONTENT)
        .expect()
                .statusCode(204)
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");

        assertArrayEquals("Output Does Not match", FILE_CONTENT, pf.data());
    }

    @Test
    public void shouldReturn412WhenUploadingAndEtagMismatch() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        givenAccess()
                .header(Names.IF_MATCH, OTHER_ETAG)
                .content(FILE_CONTENT)
        .expect()
                .statusCode(412)
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");
    }

    @Test
    public void shouldReturn404WhenUploadToNonExistingFile() throws Exception
    {
        givenAccess()
                .content(FILE_CONTENT)
        .expect()
                .statusCode(404)
        .when()
                .put("/v0.10/files/" + id(OID.generate()) + "/content");
    }

    @Test
    public void shouldReturn403WhenViewerTriesToUpload() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        doThrow(new ExNoPerm()).when(acl).checkThrows_(user, soid.sidx(), Permissions.EDITOR);

        givenAccess()
                .content(FILE_CONTENT)
        .expect()
                .statusCode(403)
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");
    }

    @Test
    public void shouldReturn403WhenTriesToUploadWithReadOnlyToken() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        givenReadAccess()
                .content(FILE_CONTENT)
        .expect()
                .statusCode(403)
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");
    }

    @Test
    public void shouldReturn429WhenOutOfUploadTokens() throws Exception
    {
        doReturn(null).when(tokenManager).acquire_(eq(Cat.API_UPLOAD), anyString());

        SOID soid = mds.root().file("foo.txt").soid();

        givenAccess()
                .content(FILE_CONTENT)
        .expect()
                .statusCode(429)
                .body("type", equalTo("TOO_MANY_REQUESTS"))
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");
    }

    @Test
    public void shouldRejectContentRangeWithoutUnit() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        givenAccess()
                .header(Names.CONTENT_RANGE, "*/*")
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
                .body("message", equalTo("Invalid header: Content-Range"))
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");
    }

    @Test
    public void shouldRejectInvalidContentRange() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        givenAccess()
                .header(Names.CONTENT_RANGE, "bytes garbage/moregarbage")
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
                .body("message", equalTo("Invalid header: Content-Range"))
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");
    }

    @Test
    public void shouldRejectIncompleteContentRange() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        givenAccess()
                .header(Names.CONTENT_RANGE, "bytes 0-/*")
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
                .body("message", equalTo("Invalid header: Content-Range"))
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");
    }

    @Test
    public void shouldObtainUploadIdentifier() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        givenAccess()
                .header(Names.CONTENT_RANGE, "bytes */*")
        .expect()
                .statusCode(204)
                .header("Upload-ID", anyUploadID())
        .when().log().everything()
                .put("/v0.10/files/" + id(soid) + "/content");
    }

    @Test
    public void shouldStartChunkedUpload() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        givenAccess()
                .header(Names.CONTENT_RANGE, "bytes 0-" + (FILE_CONTENT.length - 1) + "/*")
                .content(FILE_CONTENT)
        .expect()
                .statusCode(204)
                .header("Upload-ID", anyUploadID())
                .header(Names.RANGE, "bytes=0-" + (FILE_CONTENT.length - 1))
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");

        assertArrayEquals("Output Does Not match", FILE_CONTENT, pf.data());
    }

    @Test
    public void shouldRetrieveUploadProgress() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        pf.newOutputStream_(false).write(new byte[42]);

        UploadID id = UploadID.generate();

        givenAccess()
                .header("Upload-ID", id.toStringFormal())
                .header(Names.CONTENT_RANGE, "bytes */*")
        .expect()
                .statusCode(204)
                .header("Upload-ID", id.toStringFormal())
                .header(Names.RANGE, "bytes=0-41")
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");
    }

    @Test
    public void shouldReturn400WhenCheckingProgressForInvalidID() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        UploadID id = UploadID.generate();

        givenAccess()
                .header("Upload-ID", id.toStringFormal())
                .header(Names.CONTENT_RANGE, "bytes */*")
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
                .body("message", equalTo("Invalid upload identifier"))
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");
    }

    @Test
    public void shouldContinueChunkedUpload() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        pf.newOutputStream_(false).write(FILE_CONTENT);

        UploadID id = UploadID.generate();

        givenAccess()
                .header("Upload-ID", id.toStringFormal())
                .header(Names.CONTENT_RANGE,
                        "bytes " + FILE_CONTENT.length + "-" + (2 * FILE_CONTENT.length - 1) + "/*")
                .content(FILE_CONTENT)
        .expect()
                .statusCode(204)
                .header("Upload-ID", id.toStringFormal())
                .header(Names.RANGE, "bytes=0-" + (2 * FILE_CONTENT.length - 1))
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");

        assertArrayEquals("Output Does Not match", BaseUtil.concatenate(FILE_CONTENT, FILE_CONTENT),
                pf.data());
    }

    @Test
    public void shouldConcludeChunkedUpload() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        pf.newOutputStream_(false).write(FILE_CONTENT);

        UploadID id = UploadID.generate();

        givenAccess()
                .header("Upload-ID", id.toStringFormal())
                .header(Names.CONTENT_RANGE,
                        "bytes " + FILE_CONTENT.length + "-" + (2 * FILE_CONTENT.length - 1) + "/" +
                                (2 * FILE_CONTENT.length))
                .content(FILE_CONTENT)
        .expect()
                .statusCode(204)
                .header("Upload-ID", nullValue())
                .header(Names.ETAG, CURRENT_ETAG)
        .when().log().everything()
                .put("/v0.10/files/" + id(soid) + "/content");

        verify(ps).apply_(eq(pf), any(IPhysicalFile.class), eq(true), anyLong(), eq(t));

        assertArrayEquals("Output Does Not match", BaseUtil.concatenate(FILE_CONTENT, FILE_CONTENT),
                pf.data());
    }

    @Test
    public void shouldConcludeChunkedUploadWithEmptyChunk() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        pf.newOutputStream_(false).write(FILE_CONTENT);

        UploadID id = UploadID.generate();

        givenAccess()
                .header("Upload-ID", id.toStringFormal())
                .header(Names.CONTENT_RANGE, "bytes */" + FILE_CONTENT.length)
        .expect()
                .statusCode(204)
                .header("Upload-ID", nullValue())
                .header(Names.ETAG, CURRENT_ETAG)
        .when().log().everything()
                .put("/v0.10/files/" + id(soid) + "/content");

        verify(ps).apply_(eq(pf), any(IPhysicalFile.class), eq(true), anyLong(), eq(t));

        assertArrayEquals("Output Does Not match", FILE_CONTENT, pf.data());
    }

    @Test
    public void shouldAcceptEmptyChunk() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        pf.newOutputStream_(false).write(FILE_CONTENT);

        UploadID id = UploadID.generate();

        givenAccess()
                .header("Upload-ID", id.toStringFormal())
                .header(Names.CONTENT_RANGE, "bytes */" + (2 * FILE_CONTENT.length))
        .expect()
                .statusCode(204)
                .header("Upload-ID", id.toStringFormal())
                .header(Names.RANGE, "bytes=0-" + (FILE_CONTENT.length - 1))
        .when().log().everything()
                .put("/v0.10/files/" + id(soid) + "/content");

        verify(ps, never()).apply_(eq(pf), any(IPhysicalFile.class), eq(true), anyLong(), eq(t));

        assertArrayEquals("Output Does Not match", FILE_CONTENT, pf.data());
    }

    @Test
    public void shouldRejectChunkStartingAtNonZeroOffsetWithMissingUploadID() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        pf.newOutputStream_(false).write(new byte[42]);

        givenAccess()
                .header(Names.CONTENT_RANGE, "bytes 100-199/*")
                .content(FILE_CONTENT)
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
                .body("message", equalTo("Missing or invalid Upload-ID"))
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");
    }

    @Test
    public void shouldRejectNonContiguousChunkBackward() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        pf.newOutputStream_(false).write(new byte [42]);

        UploadID id = UploadID.generate();

        givenAccess()
                .header("Upload-ID", id.toStringFormal())
                .header(Names.CONTENT_RANGE, "bytes 0-" + (FILE_CONTENT.length - 1) + "/*")
                .content(FILE_CONTENT)
        .expect()
                .statusCode(416)
                .header("Upload-ID", id.toStringFormal())
                .header(Names.RANGE, "bytes=0-41")
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");
    }

    @Test
    public void shouldRejectNonContiguousChunkForward() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        pf.newOutputStream_(false).write(new byte [42]);

        UploadID id = UploadID.generate();

        givenAccess()
                .header("Upload-ID", id.toStringFormal())
                .header(Names.CONTENT_RANGE, "bytes 100-" + (99 + FILE_CONTENT.length) + "/*")
                .content(FILE_CONTENT)
        .expect()
                .statusCode(416)
                .header("Upload-ID", id.toStringFormal())
                .header(Names.RANGE, "bytes=0-41")
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");
    }

    @Test
    public void shouldRejectInconsistentChunkSize() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        pf.newOutputStream_(false).write(FILE_CONTENT);

        UploadID id = UploadID.generate();

        givenAccess()
                .header("Upload-ID", id.toStringFormal())
                .header(Names.CONTENT_RANGE,
                        "bytes " + FILE_CONTENT.length + "-" + (3 * FILE_CONTENT.length) + "/*")
                .content(FILE_CONTENT)
        .expect()
                .statusCode(400)
                .header("Upload-ID", id.toStringFormal())
                .body("type", equalTo("BAD_ARGS"))
                .body("message", equalTo("Content-Range not consistent with body length"))
        .when().log().everything()
                .put("/v0.10/files/" + id(soid) + "/content");

        verify(pf).delete_();
    }

    @Test
    public void shouldRejectInconsistentContentRange() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();

        UploadID id = UploadID.generate();

        givenAccess()
                .header("Upload-ID", id.toStringFormal())
                .header(Names.CONTENT_RANGE,
                        "bytes 0-10/10")
                .content(FILE_CONTENT)
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
                .body("message", equalTo("Invalid Content-Range: last-byte-pos >= instance-length (RFC2616 14.16)"))
        .when().log().everything()
                .put("/v0.10/files/" + id(soid) + "/content");
    }
}
