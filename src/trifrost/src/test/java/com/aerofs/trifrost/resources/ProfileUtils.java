package com.aerofs.trifrost.resources;

import com.aerofs.trifrost.ServerConfiguration;
import com.aerofs.trifrost.api.Device;
import com.aerofs.trifrost.api.DeviceAuthentication;
import com.aerofs.trifrost.api.EmailAddress;
import com.aerofs.trifrost.api.VerifiedDevice;
import com.google.common.io.BaseEncoding;
import com.jayway.restassured.response.Header;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.jayway.restassured.RestAssured.given;

/**
 */
public class ProfileUtils {
    public static void createVerificationCode(String email) {
        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(new EmailAddress(email))
                .post(ServerConfiguration.signupUrl())
                .then()
                .statusCode(Response.Status.OK.getStatusCode());
    }

    public static Header authHeader(VerifiedDevice dev) {
        String tmp = dev.userId + ":" + dev.accessToken;
        return new Header("Authorization", "Basic " + BaseEncoding.base64().encode(tmp.getBytes()));
    }

    static com.jayway.restassured.response.Response verifyEmail(String emailAddr) {
        return given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(DeviceAuthentication.createForAuthCode(emailAddr, "123456", new Device("dn", "df")))
                .post(ServerConfiguration.authTokenUrl());
    }

    static VerifiedDevice createUser(String email) {
        ProfileUtils.createVerificationCode(email);
        return ProfileUtils.verifyEmail(email).as(VerifiedDevice.class);
    }
}
