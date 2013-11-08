package com.aerofs.daemon.rest;

import com.aerofs.bifrost.server.BifrostTest;
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
        .when().get("/v0.9/children");
    }

    @Test
    public void shouldReturn401WhenAccessTokenInvalid() throws Exception
    {
        given()
                .queryParam("access_token", "totallynotavalidtoken")
        .expect()
                .statusCode(401)
        .when().get("/v0.9/children");
    }

    @Test
    public void shouldReturn401WhenAccessTokenExpired() throws Exception
    {
        given()
                .queryParam("access_token", BifrostTest.EXPIRED)
        .expect()
                .statusCode(401)
        .when().get("/v0.9/children");
    }
}
