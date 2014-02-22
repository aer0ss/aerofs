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
        given()
                .header(Names.AUTHORIZATION, "Bearer totallynotavalidtoken")
        .expect()
                .statusCode(401)
                .header(Names.WWW_AUTHENTICATE, "Bearer realm=\"AeroFS\"")
        .when().get("/v0.9/children");
    }

    @Test
    public void shouldReturn401WhenAccessTokenExpired() throws Exception
    {
        given()
                .header(Names.AUTHORIZATION, "Bearer " + BifrostTest.EXPIRED)
        .expect()
                .statusCode(401)
                .header(Names.WWW_AUTHENTICATE, "Bearer realm=\"AeroFS\"")
        .when().get("/v0.9/children");
    }

    @Test
    public void shouldAcceptTokenInQueryParam() throws Exception
    {
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
}
