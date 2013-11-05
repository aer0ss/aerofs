/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.base.Base64;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.proto.Sp.AuthorizeMobileDeviceReply;
import com.jayway.restassured.response.Response;
import org.junit.Test;

import java.util.Map;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.path.json.JsonPath.from;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

/**
 */
public class TestTokenResource extends BifrostTest
{
    @Test
    public void shouldHandleBadAccessCode() throws Exception
    {
        when(_spClient.authorizeMobileDevice(anyString(), anyString()))
                .thenThrow(new ExBadCredential());

        expect()
                .statusCode(400)
        .given()
                .formParam("client_id", CLIENTID)
                .formParam("client_secret", CLIENTSECRET)
                .formParam("grant_type", "authorization_code")
                .formParam("code_type", "device_authorization")
                .formParam("code", "magic")
                .post(TOKEN_URL);
    }

    @Test
    public void shouldRejectBadClientPassword() throws Exception
    {
        when(_spClient.authorizeMobileDevice(anyString(), anyString())).thenReturn(
                AuthorizeMobileDeviceReply.newBuilder()
                        .setUserId("test1@b.c")
                        .setOrgId("2")
                        .setIsOrgAdmin(true)
                        .build());

        expect()
                .statusCode(401)
        .given()
                .header("Authorization", buildAuthHeader(CLIENTID, "bad-secret"))
                .formParam("client_id", CLIENTID)
                .formParam("grant_type", "authorization_code")
                .formParam("code_type", "device_authorization")
                .formParam("code", "magic")
                .post(TOKEN_URL);
    }

    @Test
    public void shouldGetTokenForMobileAccessCode() throws Exception
    {
        when(_spClient.authorizeMobileDevice(anyString(), anyString())).thenReturn(
                AuthorizeMobileDeviceReply.newBuilder()
                    .setUserId("test1@b.c")
                    .setOrgId("2")
                    .setIsOrgAdmin(true)
                    .build());

        String response = given()
                .header("Authorization", buildAuthHeader(CLIENTID, CLIENTSECRET))
                .formParam("client_id", CLIENTID)
                .formParam("grant_type", "authorization_code")
                .formParam("code_type", "device_authorization")
                .formParam("code", "magic")
                .post(TOKEN_URL).asString();

        String token = from(response).get("access_token");
        assertNotNull(token);
        assertFalse(token.isEmpty());

        assertNotNull(from(response).get("token_type"));
        assertNotNull(from(response).get("scope"));
    }

    @Test
    public void shouldGetTokenForOAuthAccessCode() throws Exception
    {
        // First, get an auth code from the authorization endpoint...
        String authState = oauthBegin();
        Response response = given()
                .formParam("j_username", "foo")
                .formParam("j_password", "bar")
                .formParam("auth_state", authState)
                .post(AUTH_URL);

        assertEquals(response.getStatusCode(), 303);
        Map<String, String> q = extractQuery(response.getHeader("Location"));
        assertTrue(q.containsKey("code"));
        assertTrue(q.get("code").length() > 0);

        String tokenResponse = given()
                .header("Authorization", buildAuthHeader(CLIENTID, CLIENTSECRET))
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("client_id", CLIENTID)
                .formParam("grant_type", "authorization_code")
                .formParam("code", q.get("code"))
                .post(TOKEN_URL).asString();

        String token = from(tokenResponse).get("access_token");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    /** Positive test case but using client_secret form-param allowed by OAuth standard */
    @Test
    public void shouldSupportClientSecret() throws Exception
    {
        when(_spClient.authorizeMobileDevice(anyString(), anyString())).thenReturn(
                AuthorizeMobileDeviceReply.newBuilder()
                        .setUserId("test1@b.c")
                        .setOrgId("2")
                        .setIsOrgAdmin(true)
                        .build());

        String response = given()
                .formParam("client_id", CLIENTID)
                .formParam("client_secret", CLIENTSECRET)
                .formParam("grant_type", "authorization_code")
                .formParam("code_type", "device_authorization")
                .formParam("code", "magic")
             .post(TOKEN_URL).asString();

        String token = from(response).get("access_token");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    public void shouldRejectBadGrantType() throws Exception
    {
        Response post = given()
                .formParam("grant_type", "I_am_a_bad_grant_type")
                .formParam("code_type", "device_authorization")
                .formParam("authorization_code", "magic")
                .formParam("redirect_uri", CLIENTREDIRECT)
            .post(TOKEN_URL);

        assertEquals(400, post.getStatusCode());
        assertEquals("unsupported_grant_type", from(post.asString()).get("error"));
    }

    private String buildAuthHeader(String user, String password)
    {
        String mashup = user + ":" + password;
        return "Basic " + Base64.encodeBytes(mashup.getBytes());
    }
}
