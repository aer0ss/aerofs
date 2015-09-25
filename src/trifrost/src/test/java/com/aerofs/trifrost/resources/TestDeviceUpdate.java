package com.aerofs.trifrost.resources;

import com.aerofs.trifrost.ServerConfiguration;
import com.aerofs.trifrost.TrifrostTestResource;
import com.aerofs.trifrost.Utilities;
import com.aerofs.trifrost.api.Device;
import com.aerofs.trifrost.api.VerifiedDevice;
import com.jayway.restassured.RestAssured;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.jayway.restassured.RestAssured.given;

public final class TestDeviceUpdate {
    static { RestAssured.config = Utilities.newRestAssuredConfig(); }
    @Rule public RuleChain profileServer = TrifrostTestResource.toRuleChain();


    /*** update ***/
    // PUT /devices/{deviceid}
    @Test
    public void shouldNotAllowUnauthorizedUser() throws Exception {
        String email = "test@user.com";
        VerifiedDevice device = ProfileUtils.createUser(email);

        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .header(ProfileUtils.authHeader(device))
                .body(new Device("new name", "new family"))
                .put(ServerConfiguration.deviceUrl("bad_device_id"))
                .then()
                .statusCode(Response.Status.FORBIDDEN.getStatusCode());
    }

    // PUT /{userid}/device/{deviceid}
    @Test
    public void shouldNotAllowEmptyDevice() throws Exception {
        String email = "test@user.com";
        VerifiedDevice device = ProfileUtils.createUser(email);

        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .header(ProfileUtils.authHeader(device))
                .body("{}")
                .put(ServerConfiguration.deviceUrl(device.deviceId))
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());
    }

    // PUT /{userid}/device/{deviceid}
    @Test
    public void shouldAllowGoodDevice() throws Exception {
        String email = "test@user.com";
        VerifiedDevice device = ProfileUtils.createUser(email);

        // null push type
        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .header(ProfileUtils.authHeader(device))
                .body(new Device("new name", "new family"))
                .put(ServerConfiguration.deviceUrl(device.deviceId))
                .then()
                .statusCode(Response.Status.CREATED.getStatusCode());

        // none push type
        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .header(ProfileUtils.authHeader(device))
                .body(new Device("new name", "new family", Device.PushType.NONE, null))
                .put(ServerConfiguration.deviceUrl(device.deviceId))
                .then()
                .statusCode(Response.Status.CREATED.getStatusCode());
        // active push type
        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .header(ProfileUtils.authHeader(device))
                .body(new Device("new name", "new family", Device.PushType.APNS, "abc123"))
                .put(ServerConfiguration.deviceUrl(device.deviceId))
                .then()
                .statusCode(Response.Status.CREATED.getStatusCode());
    }
}