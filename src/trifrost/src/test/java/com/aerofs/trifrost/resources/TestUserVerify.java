package com.aerofs.trifrost.resources;

import com.aerofs.trifrost.ServerConfiguration;
import com.aerofs.trifrost.TrifrostTestResource;
import com.aerofs.trifrost.Utilities;
import com.aerofs.trifrost.api.DeviceAuthentication;
import com.jayway.restassured.RestAssured;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.jayway.restassured.RestAssured.given;

public final class TestUserVerify {
    static { RestAssured.config = Utilities.newRestAssuredConfig(); }
    @Rule public RuleChain profileServer = TrifrostTestResource.toRuleChain();

    @Test
    public void registerDeviceOk() throws Exception {
        String em = "registerDeviceOk@test.foo";
        ProfileUtils.createVerificationCode(em);
        ProfileUtils.verifyEmail(em).then()
                .body("access_token", Matchers.notNullValue())
                .body("domain", Matchers.notNullValue())
                .statusCode(Response.Status.OK.getStatusCode());
    }

    // POST /auth/token
    @Test
    public void shouldFailBadVerify() throws Exception {
        DeviceAuthentication auth = DeviceAuthentication.createForAuthCode("a@b.c", "bad_code");

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(auth)
                .post(ServerConfiguration.authTokenUrl())
                .then()
                .statusCode(Response.Status.FORBIDDEN.getStatusCode());
    }

    // POST /auth/token
    @Test
    public void shouldAcceptWithDevice() throws Exception {
        String em = "shouldAcceptWithDevice@test.foo";
        ProfileUtils.createVerificationCode(em);
        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(DeviceAuthentication.createForAuthCode(em, "123456"))
                .post(ServerConfiguration.authTokenUrl())
                .then()
                .body("access_token", Matchers.notNullValue())
                .body("domain", Matchers.notNullValue())
                .statusCode(Response.Status.OK.getStatusCode());
    }
}