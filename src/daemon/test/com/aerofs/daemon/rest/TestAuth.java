package com.aerofs.daemon.rest;

import com.aerofs.bifrost.server.BifrostTest;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.junit.Test;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.given;

public class TestAuth extends AbstractRestTest
{
    public TestAuth(boolean useProxy)
    {
        super(useProxy);
    }

    @Test
    public void shouldReturn401WhenAccessTokenMissing() throws Exception
    {
        expect()
                .statusCode(401)
                .header(Names.WWW_AUTHENTICATE, "Bearer realm=\"AeroFS\"")
        .when().get("/v0.9/children");
    }

    @Test
    public void shouldReturn401WhenAccessTokenInvalid() throws Exception
    {
        givenInvalidToken()
        .expect()
                .statusCode(401)
                .header(Names.WWW_AUTHENTICATE, "Bearer realm=\"AeroFS\"")
        .when().get("/v0.9/children");
    }

    @Test
    public void shouldReturn401WhenAccessTokenExpired() throws Exception
    {
        givenExpiredToken()
        .expect()
                .statusCode(401)
                .header(Names.WWW_AUTHENTICATE, "Bearer realm=\"AeroFS\"")
        .when().get("/v0.9/children");
    }

    @Test
    public void shouldAcceptTokenInQueryParam() throws Exception
    {
        // stub TokenVerifier
        givenAccess();

        given()
                .queryParam("token", BifrostTest.RW_TOKEN)
        .expect()
                .statusCode(200)
        .when().get("/v1.0/children");
    }

    @Test
    public void shouldReturn401WhenAccessTokenGivenInQueryParamAndAuthHeader() throws Exception
    {
        given()
                .queryParam("token", BifrostTest.RW_TOKEN)
                .header(Names.AUTHORIZATION, "Bearer " + BifrostTest.RW_TOKEN)
        .expect()
                .statusCode(401)
        .when().get("/v1.0/children");
    }

    @Test
    public void shouldReturn401WhenAccessTokenGivenInTwoQueryParams() throws Exception
    {
        given()
                .queryParam("token", BifrostTest.RW_TOKEN)
                .queryParam("token", BifrostTest.RW_TOKEN)
        .expect()
                .statusCode(401)
        .when().get("/v1.0/children");
    }

    @Test
    public void shouldReturn401WhenAccessTokenGivenInTwoAuthHeaders() throws Exception
    {
        given()
                .header(Names.AUTHORIZATION, "Bearer " + BifrostTest.RW_TOKEN)
                .header(Names.AUTHORIZATION, "Bearer " + BifrostTest.RW_TOKEN)
        .expect()
                .statusCode(401)
        .when().get("/v1.0/children");
    }

    @Test
    public void shouldGetFileWithTokenScopedToFile() throws Exception
    {
        mds.root().dir("d1").file("f1");
        mds.root().file("f2");

        givenReadAccessTo(object("d1/f1"))
        .expect()
                .statusCode(200)
        .when().log().everything()
                .get("/v1.2/files/" + object("d1/f1").toStringFormal());
    }

    @Test
    public void shouldGet403WhenTokenScopedToDifferentFile() throws Exception
    {
        mds.root().dir("d1").file("f1");
        mds.root().file("f2");

        givenReadAccessTo(object("f2"))
        .expect()
                .statusCode(403)
        .when()
                .get("/v1.2/files/" + object("d1/f1").toStringFormal());
    }

    @Test
    public void shouldGetFileWithTokenScopedToParent() throws Exception
    {
        mds.root().dir("d1").file("f1");
        mds.root().file("f2");

        givenReadAccessTo(object("d1"))
        .expect()
                .statusCode(200)
        .when()
                .get("/v1.2/files/" + object("d1/f1").toStringFormal());
    }

    @Test
    public void shouldGetFolderWithTokenScopedToFolder() throws Exception
    {
        mds.root().dir("d1").file("f1");
        mds.root().file("f2");


        givenReadAccessTo(object("d1"))
        .expect()
                .statusCode(200)
        .when()
                .get("/v1.2/folders/" + object("d1").toStringFormal());
    }

    @Test
    public void shouldGetFolderWithTokenScopedToParentFolder() throws Exception
    {
        mds.root().dir("d1").dir("d2").file("f1");

        givenReadAccessTo(object("d1"))
        .expect()
                .statusCode(200)
        .when()
                .get("/v1.2/folders/" + object("d1/d2").toStringFormal());
    }

    @Test
    public void shouldGet403WhenTokenScopedToChildFolder() throws Exception
    {
        mds.root().dir("d1").dir("d2").file("f1");

        givenReadAccessTo(object("d1/d2"))
        .expect()
                .statusCode(403)
        .when()
                .get("/v1.2/folders/" + object("d1").toStringFormal());
    }
}
