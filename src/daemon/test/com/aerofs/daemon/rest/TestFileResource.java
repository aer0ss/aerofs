package com.aerofs.daemon.rest;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SOID;
import com.google.common.io.ByteStreams;
import com.jayway.restassured.response.Response;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.util.Arrays;
import java.util.Date;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isEmptyString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class TestFileResource extends AbstractRestTest
{
    private final String RESOURCE = "/v0.8/files/{file}";

    private static long FILE_MTIME = 0xdeadbeef;
    private static byte[] FILE_CONTENT = { 'H', 'e', 'l', 'l', 'o'};
    private static byte[] VERSION_HASH = BaseSecUtil.newMessageDigestMD5().digest(FILE_CONTENT);

    @Before
    public void mockFile() throws Exception
    {
        mds.root().file("f1").caMaster(FILE_CONTENT.length, FILE_MTIME).content(FILE_CONTENT);
        when(nvc.getVersionHash_(any(SOID.class))).thenReturn(VERSION_HASH);
    }

    @Test
    public void shouldReturn400ForInvalidId() throws Exception
    {
        expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when().get(RESOURCE, "bla");
    }

    @Test
    public void shouldReturn404ForNonExistingStore() throws Exception
    {
        expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, new RestObject(SID.generate(), OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn404ForNonExistingDir() throws Exception
    {
        expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn404ForDir() throws Exception
    {
        mds.root().dir("d0");

        expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, object("d0").toStringFormal());
    }

    @Test
    public void shouldGetMetadata() throws Exception
    {
        expect()
                .statusCode(200)
                .body("id", equalTo(object("f1").toStringFormal()))
                .body("name", equalTo("f1"))
                .body("size", equalTo(FILE_CONTENT.length))
                .body("last_modified", equalTo(ISO_8601.format(new Date(FILE_MTIME))))
        .when().get(RESOURCE, object("f1").toStringFormal());
    }

    @Test
    public void shouldGet406IfNotAcceptingOctetStream() throws Exception
    {
        given()
                .header("Accept", "application/json")
        .expect()
                .statusCode(406)
        .when().get(RESOURCE + "/content", object("f1").toStringFormal());
    }

    @Test
    public void shouldGetContent() throws Exception
    {
        byte[] content =
        given()
                .header("Accept", "*/*")
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Content-Length", String.valueOf(FILE_CONTENT.length))
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGet304WhenEtagUnchanged() throws Exception
    {
        given()
                .header("Accept", "*/*")
                .header("If-None-Match", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .expect()
                .statusCode(304)
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
                .content(isEmptyString())
        .when().get(RESOURCE + "/content", object("f1").toStringFormal());
    }

    @Test
    public void shouldGetContentWhenEtagChanged() throws Exception
    {
        byte[] content =
        given()
                .header("Accept", "*/*")
                .header("If-None-Match", "\"f00\"")
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Content-Length", String.valueOf(FILE_CONTENT.length))
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGetContentWhenEtagInvalid() throws Exception
    {
        byte[] content =
        given()
                .header("Accept", "*/*")
                .header("If-None-Match", "lowut")
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Content-Length", String.valueOf(FILE_CONTENT.length))
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGetRangeWhenIfRangeMatchEtag() throws Exception
    {
        byte[] content =
        given()
                .header("Accept", "*/*")
                .header("Range", "bytes=0-1")
                .header("If-Range", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .expect()
                .statusCode(206)
                .contentType("application/octet-stream")
                .header("Content-Length", "2")
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
        given()
                .header("Accept", "*/*")
                .header("Range", "bytes=0-1")
                .header("If-Range", "\"f00\"")
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Content-Length", String.valueOf(FILE_CONTENT.length))
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
        given()
                .header("Accept", "*/*")
                .header("Range", "bytes=0-1")
                .header("If-Range", "lolwut")
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Content-Length", String.valueOf(FILE_CONTENT.length))
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGetFullContentWhenRangeInvalid() throws Exception
    {
        byte[] content =
        given()
                .header("Accept", "*/*")
                .header("Range", "bytes=1-0")
                .header("If-Range", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .expect()
                .statusCode(200)
                .contentType("application/octet-stream")
                .header("Content-Length", String.valueOf(FILE_CONTENT.length))
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldReturn416WhenRangeNotSatisfiable() throws Exception
    {
        given()
                .header("Accept", "*/*")
                .header("Range", "bytes=" + (FILE_CONTENT.length) + "-")
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
        given()
                .header("Accept", "*/*")
                .header("Range", "bytes=0-2,3-")
                .header("If-Range", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .expect()
                .statusCode(206)
                .contentType("application/octet-stream")
                .header("Content-Range", "bytes 0-" + String.valueOf(FILE_CONTENT.length - 1)
                        + "/" + String.valueOf(FILE_CONTENT.length))
                .header("Content-Length", String.valueOf(FILE_CONTENT.length))
                .header("Etag", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .when().get(RESOURCE + "/content", object("f1").toStringFormal())
                .body().asByteArray();

        Assert.assertArrayEquals(FILE_CONTENT, content);
    }

    @Test
    public void shouldGetMultipartWhenDisjointSubrangesRequested() throws Exception
    {
        Response r =
        given()
                .header("Accept", "*/*")
                .header("Range", "bytes=0-1,3-")
                .header("If-Range", String.format("\"%s\"", BaseUtil.hexEncode(VERSION_HASH)))
        .expect()
                .statusCode(206)
                .contentType("multipart/byteranges")
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
}
