/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.auditor.server;

import com.aerofs.auth.client.cert.AeroDeviceCert;
import com.aerofs.base.BaseSecUtil;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.google.gson.JsonObject;
import com.jayway.restassured.http.ContentType;
import org.junit.Test;

import java.util.Date;

import static com.jayway.restassured.RestAssured.expect;

/*
 * Tests both the LegacyAuthenticator and the non-legacy authenticator.
 */
public class TestAuthenticators extends AuditorTest
{
    protected static final UserID user = UserID.fromInternal("foo@bar.baz");
    protected static final DID did = DID.generate();

    @Test
    public void shouldRequireAuthFields()
    {
        expect()
                .statusCode(400)
        .given().contentType(ContentType.JSON)
                .header("Aerofs-Auth-Required", "True")
                .body(getMinimalEvent().toString())
        .when().post(AUDIT_URL);
    }

    @Test
    public void shouldRequireUID()
    {
        expect()
                .statusCode(400)
        .given().contentType(ContentType.JSON)
                .header("Aerofs-Auth-Required", "True")
                .header("AeroFS-DeviceID", did.toStringFormal())
                .header("Verify", "SUCCESS")
                .header("DName", getDName())
                .body(getMinimalEvent().toString())
        .when().post(AUDIT_URL);
    }

    @Test
    public void shouldRequireDeviceID()
    {
        expect()
                .statusCode(400)
        .given().contentType(ContentType.JSON)
                .header("Aerofs-Auth-Required", "True")
                .header("AeroFS-UserID", user.getString())
                .header("Verify", "SUCCESS")
                .header("DName", getDName())
                .body(getMinimalEvent().toString())
        .when().post(AUDIT_URL);
    }

    @Test
    public void shouldRequireVerification()
    {
        expect()
                .statusCode(403)
        .given().contentType(ContentType.JSON)
                .header("Aerofs-Auth-Required", "True")
                .header("AeroFS-UserID", user.getString())
                .header("AeroFS-DeviceID", did.toStringFormal())
                .header("Verify", "None")
                .header("DName", getDName())
                .body(getMinimalEvent().toString())
        .when().post(AUDIT_URL);
    }

    @Test
    public void shouldHandleDNameError()
    {
        expect()
                .statusCode(400)
        .given().contentType(ContentType.JSON)
                .header("Aerofs-Auth-Required", "True")
                .header("AeroFS-UserID", user.getString())
                .header("AeroFS-DeviceID", did.toStringFormal())
                .header("Verify", "SUCCESS")
                .header("DName", "OU=A/BC=DE")
                .body(getMinimalEvent().toString())
        .when().post(AUDIT_URL);
    }

    @Test
    public void shouldHandleCNameMismatch()
    {
        expect()
                .statusCode(403)
        .given().contentType(ContentType.JSON)
                .header("Aerofs-Auth-Required", "True")
                .header("AeroFS-UserID", user.getString())
                .header("AeroFS-DeviceID", did.toStringFormal())
                .header("Verify", "SUCCESS")
                .header("DName", "OU=A/BC=DE/CN=MY FRIEND JOE")
                .body(getMinimalEvent().toString())
        .when().post(AUDIT_URL);
    }

    private String getDName()
    {
        return "OU=whatever/CN=" + BaseSecUtil.getCertificateCName(user, did);
    }

    @Test
    public void shouldAuthenticate()
    {
        expect()
                .statusCode(200)
        .given().contentType(ContentType.JSON)
                .header("AeroFS-Auth-Required", "True")
                .header("AeroFS-UserID", user.getString())
                .header("AeroFS-DeviceID", did.toStringFormal())
                .header("Verify", "SUCCESS")
                .header("DName", getDName())
                .body(getMinimalEvent().toString())
        .when().post(AUDIT_URL);
    }

    @Test
    public void shouldAuthenticateUsingNonLegacyAuthenticator()
    {
        expect()
                .statusCode(200)
        .given().contentType(ContentType.JSON)
                .header("Authorization", AeroDeviceCert.getHeaderValue(user.getString(), did.toStringFormal()))
                .header("Verify", "SUCCESS")
                .header("DName", getDName())
                .body(getMinimalEvent().toString())
        .when().post(AUDIT_URL);
    }

    private JsonObject getMinimalEvent()
    {
        JsonObject obj = new JsonObject();
        obj.addProperty("timestamp", new Date().toString());
        obj.addProperty("topic", "user");
        obj.addProperty("event", "sign_in");
        return obj;
    }
}