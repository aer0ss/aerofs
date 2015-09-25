package com.aerofs.trifrost.resources;

import com.aerofs.trifrost.ServerConfiguration;
import com.aerofs.trifrost.TrifrostTestResource;
import com.aerofs.trifrost.Utilities;
import com.aerofs.trifrost.api.Device;
import com.aerofs.trifrost.api.DeviceAuthentication;
import com.aerofs.trifrost.api.RefreshToken;
import com.aerofs.trifrost.api.VerifiedDevice;
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
                .body("refresh_token", Matchers.notNullValue())
                .body("device_id", Matchers.notNullValue())
                .body("user_id", Matchers.notNullValue())
                .body("domain", Matchers.notNullValue())
                .statusCode(Response.Status.OK.getStatusCode());
    }

    // POST /auth/token
    @Test
    public void shouldFailBadVerify() throws Exception {
        DeviceAuthentication auth = DeviceAuthentication.createForAuthCode("a@b.c", "bad_code", null);

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
                .body(DeviceAuthentication.createForAuthCode(em, "123456", new Device("dn1", "df1")))
                .post(ServerConfiguration.authTokenUrl())
                .then()
                .body("access_token", Matchers.notNullValue())
                .body("refresh_token", Matchers.notNullValue())
                .body("device_id", Matchers.notNullValue())
                .body("user_id", Matchers.notNullValue())
                .body("domain", Matchers.notNullValue())
                .statusCode(Response.Status.OK.getStatusCode());
    }



    // POST /auth/token
    @Test
    public void shouldUseRefreshToken() throws Exception {
        String em = "shouldUseRefreshToken@test.foo";
        VerifiedDevice device = ProfileUtils.createUser(em);

        DeviceAuthentication refresh = DeviceAuthentication.createForRefreshCode(device.refreshToken, device.userId, null);

        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(refresh)
                .post(ServerConfiguration.authTokenUrl())
                .then()
                .body("access_token", Matchers.not(Matchers.equalTo(device.accessToken)))
                .body("refresh_token", Matchers.not(Matchers.equalTo(device.refreshToken)))
                .body("user_id", Matchers.equalTo(device.userId))
                .body("domain", Matchers.equalTo(device.domain));
    }

    // DELETE /auth/refresh
    @Test
    public void shouldInvalidateRefreshToken() throws Exception {
        String em = "shouldInvalidateRefreshToken@test.foo";
        VerifiedDevice device = ProfileUtils.createUser(em);

        RefreshToken ref = new RefreshToken(device.refreshToken);

        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .header(ProfileUtils.authHeader(device))
                .body(ref)
                .delete(ServerConfiguration.refreshTokenUrl())
                .then()
                .statusCode(Response.Status.NO_CONTENT.getStatusCode());
    }

    // DELETE /auth/refresh
    @Test
    public void shouldFailInvalidateBadRefreshToken() throws Exception {
        String em = "shouldFailInvalidateBadRefreshToken@test.foo";
        VerifiedDevice device = ProfileUtils.createUser(em);

        RefreshToken ref = new RefreshToken("Hi mom");

        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .header(ProfileUtils.authHeader(device))
                .body(ref)
                .delete(ServerConfiguration.refreshTokenUrl())
                .then()
                .statusCode(Response.Status.FORBIDDEN.getStatusCode());
    }
}