/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.base.Base64;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.bifrost.oaaas.model.AccessToken;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.proto.Sp.AuthorizeMobileDeviceReply;
import com.google.common.collect.Sets;
import com.jayway.restassured.response.Response;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.path.json.JsonPath.from;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

        // Verify the token has orgid and userid:
        String verifyResponse = given()
                .header("Authorization", buildAuthHeader(RESOURCEKEY, RESOURCESECRET))
                .queryParam("access_token", token)
                .get("/tokeninfo").asString();

        Map<String, Map<String, String>> princ = from(verifyResponse).get("principal");
        Map<String, String> attr = princ.get("attributes");
        assertNotNull(attr.get("orgid"));
        assertNotNull(attr.get("userid"));
    }

    @Test
    public void shouldGetTokenForOAuthAccessCode() throws Exception
    {
        // make any nonce valid
        when(_spClient.authorizeMobileDevice(anyString(), anyString())).thenReturn(
                AuthorizeMobileDeviceReply.newBuilder()
                        .setUserId("test1@b.c")
                        .setOrgId("2")
                        .setIsOrgAdmin(true)
                        .build());

        // get an auth code from the authorization endpoint
        Response response = given()
                .formParam("response_type", "code")
                .formParam("client_id", CLIENTID)
                .formParam("nonce", "noooooonce")
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("state", "echoechoechoechoecho")
                .formParam("scope", "user.read")
                .post(AUTH_URL);

        assertEquals(302, response.getStatusCode());
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

        // trying the same auth code again should fail
        expect()
                .statusCode(400)
        .given()
                .header("Authorization", buildAuthHeader(CLIENTID, CLIENTSECRET))
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("client_id", CLIENTID)
                .formParam("grant_type", "authorization_code")
                .formParam("code", q.get("code"))
                .post(TOKEN_URL);
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

    @Test
    public void shouldGet400OnGetTokenListWithNoOwner() throws Exception
    {
        expect()
                .statusCode(400)
        .given()
                .get(TOKENLIST_URL);
    }

    @Test
    public void shouldListTokensOnGet() throws Exception
    {
        Response response = given()
                .queryParam("owner", USERNAME)
                .get(TOKENLIST_URL);

        assertEquals(200, response.getStatusCode());

        List<Map> tokens = from(response.asString()).get("tokens");
        assertNotNull(tokens);
        assertTrue(tokens.size() >= 1);

        for (Map t : tokens) {
            assertNotNull(t.get("client_id"));
            assertNotNull(t.get("client_name"));
            assertNotNull(t.get("expires"));
            assertNotNull(t.get("token"));
        }
    }

    @Test
    public void shouldGet404OnDeleteTokenIfTokenDNE() throws Exception
    {
        expect()
                .statusCode(404)
        .given()
                .delete(TOKEN_URL + "/no-token-by-this-name");
    }

    @Test
    public void shouldDeleteTokenOnDeleteTokenRequest() throws Exception
    {
        final String tokenVal = "this-token-will-be-deleted";

        AccessToken token = new AccessToken(
                tokenVal,
                new AuthenticatedPrincipal(USERNAME),
                _clientRepository.findByClientId(CLIENTID),
                0,
                Sets.newHashSet("read"),
                "");
        _accessTokenRepository.save(token);
        _session.flush();

        expect()
                .statusCode(200)
        .given()
                .delete(TOKEN_URL + "/" + tokenVal);

        assertNull(_accessTokenRepository.findByToken(tokenVal));
    }

    private String buildAuthHeader(String user, String password)
    {
        String mashup = user + ":" + password;
        return "Basic " + Base64.encodeBytes(mashup.getBytes());
    }
}
