/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.auth.client.shared.AeroService;
import com.google.gson.JsonObject;
import com.jayway.restassured.http.ContentType;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;

import static com.jayway.restassured.RestAssured.expect;

public class TestEventResource extends AuditorTest
{
    @Test
    public void shouldAcceptMinimalEvent()
    {
        JsonObject postBody = getMinimalEvent();

        expect()
                .statusCode(200)
        .given().contentType(ContentType.JSON)
                .header("Authorization", AeroService.getHeaderValue("sparta", AuditorTestServer.getTestDeploymentSecret()))
                .body(postBody.toString())
        .when().post(AUDIT_URL);
    }

    @Test
    public void shouldAcceptFancyEvent()
    {
        JsonObject postBody = getMinimalEvent();
        postBody.addProperty("hi mom", "there is a space here");
        postBody.addProperty("a float", 1.234);
        postBody.addProperty("a bool", true);

        expect()
                .statusCode(200)
        .given().contentType(ContentType.JSON)
                .header("Authorization", AeroService.getHeaderValue("sparta", AuditorTestServer.getTestDeploymentSecret()))
                .body(postBody.toString())
                .when().post(AUDIT_URL);
    }

    @Test
    public void shouldDetectSendFailure()
    {
        JsonObject postBody = getMinimalEvent();
        postBody.addProperty("hi mom", "there is a space here");
        postBody.addProperty("a float", 1.234);
        postBody.addProperty("a bool", true);

        _service.setDownstreamFailureCause(new IOException("shouldDetectSendFailure"));

        expect()
                .statusCode(500)
        .given().contentType(ContentType.JSON)
                .header("Authorization", AeroService.getHeaderValue("sparta", AuditorTestServer.getTestDeploymentSecret()))
                .body(postBody.toString())
                .when().post(AUDIT_URL);
    }

    @Test
    public void shouldRejectEmptyDoc()
    {
        expect()
                .statusCode(400)
        .given()
                .contentType(ContentType.JSON)
                .header("Authorization", AeroService.getHeaderValue("sparta", AuditorTestServer.getTestDeploymentSecret()))
                .content("{}")
                .post(AUDIT_URL);
    }

    @Test
    public void shouldRequireJson()
    {
        expect()
                .statusCode(415) // unsupported media type
        .given()
                .content("{}")
                .header("Authorization", AeroService.getHeaderValue("sparta", AuditorTestServer.getTestDeploymentSecret()))
                .post(AUDIT_URL);
    }

    @Test
    public void shouldRejectGet()
    {
        expect()
                .statusCode(405)
        .given()
                .header("Authorization", AeroService.getHeaderValue("sparta", AuditorTestServer.getTestDeploymentSecret()))
                .get(AUDIT_URL);
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
