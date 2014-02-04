package com.aerofs.daemon.rest;

import org.junit.Test;

import static com.jayway.restassured.RestAssured.expect;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

public class TestVersionResource extends AbstractRestTest
{
    public TestVersionResource(boolean useProxy)
    {
        super(useProxy);
    }

    @Test
    public void shouldReturnVersion()
    {
        assumeFalse(useProxy);

        expect()
                .statusCode(200)
                .body("major", equalTo(RestService.HIGHEST_SUPPORTED_VERSION.major))
                .body("minor", equalTo(RestService.HIGHEST_SUPPORTED_VERSION.minor))
        .when()
                .get("/version");
    }

    @Test
    public void shouldFindClientForLowerVersion()
    {
        givenAccess()
        .expect()
                .statusCode(200)
        .when()
                .get("/v0.8/children");
    }

    @Test
    public void shouldNotFindClientForHigherMinorVersion()
    {
        assumeTrue(useProxy);

        givenAccess()
        .expect()
                .statusCode(503)
        .when()
                .get("/v" + RestService.HIGHEST_SUPPORTED_VERSION.nextMinor() + "/children");
    }

    @Test
    public void shouldNotFindClientForHigherMajorVersion()
    {
        assumeTrue(useProxy);

        givenAccess()
        .expect()
                .statusCode(503)
        .when()
                .get("/v" + RestService.HIGHEST_SUPPORTED_VERSION.nextMajor() + "/children");
    }
}
