package com.aerofs.daemon.rest;

import org.junit.Test;

import static com.jayway.restassured.RestAssured.expect;

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
}
