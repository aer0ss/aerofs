/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.base.Base64;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.UserID;
import com.aerofs.bifrost.oaaas.model.AccessToken;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.proto.Sp.AuthorizeAPIClientReply;
import com.google.common.collect.Sets;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

/**
 */
public class TestTokenResource extends BifrostTest
{
    private static final String GOOD_NONCE = "noncy-reagan";
    private static final String BAD_NONCE = "noncing-to-see-here";
    private static final String VALID_SOID =
            "df084c5033083b540e7730a2b29d928b457b7a71fbd74d86add7c4f0a56d2093";

    @Before
    public void setUpSPResponses() throws Exception
    {
        when(_spClient.authorizeAPIClient(eq(GOOD_NONCE), anyString())).thenReturn(
                AuthorizeAPIClientReply.newBuilder()
                        .setUserId(USERNAME)
                        .setOrgId("2")
                        .setIsOrgAdmin(true)
                        .build());

        when(_spClient.authorizeAPIClient(eq(BAD_NONCE), anyString()))
                .thenThrow(new ExBadCredential());
    }

    @Test
    public void shouldHandleBadAccessCode() throws Exception
    {
        expect()
                .statusCode(400)
        .given()
                .formParam("client_id", CLIENTID)
                .formParam("client_secret", CLIENTSECRET)
                .formParam("grant_type", "authorization_code")
                .formParam("code_type", "device_authorization")
                .formParam("code", BAD_NONCE)
                .post(TOKEN_URL);
    }

    @Test
    public void shouldRejectBadClientPassword() throws Exception
    {
        expect()
                .statusCode(401)
        .given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(CLIENTID, "bad-secret"))
                .formParam("client_id", CLIENTID)
                .formParam("grant_type", "authorization_code")
                .formParam("code_type", "device_authorization")
                .formParam("code", GOOD_NONCE)
                .post(TOKEN_URL);
    }

    @Test
    public void shouldGetTokenForMobileAccessCode() throws Exception
    {
        String response = given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(CLIENTID, CLIENTSECRET))
                .formParam("client_id", CLIENTID)
                .formParam("grant_type", "authorization_code")
                .formParam("code_type", "device_authorization")
                .formParam("code", GOOD_NONCE)
                .post(TOKEN_URL).asString();

        String token = from(response).get("access_token");
        assertNotNull(token);
        assertFalse(token.isEmpty());

        assertNotNull(from(response).get("token_type"));
        assertNotNull(from(response).get("scope"));

        // Verify the token has orgid and userid:
        String verifyResponse = given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(RESOURCEKEY, RESOURCESECRET))
                .queryParam("access_token", token)
                .get(TOKENINFO_URL).asString();

        Map<String, Map<String, String>> princ = from(verifyResponse).get("principal");
        Map<String, String> attr = princ.get("attributes");
        assertNotNull(attr.get("orgid"));
        assertNotNull(attr.get("userid"));
    }

    private String getCodeFromAuthorizationEndpoint(String scope) throws Exception
    {
        Response response = given()
                .formParam("response_type", "code")
                .formParam("client_id", CLIENTID)
                .formParam("nonce", GOOD_NONCE)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("state", "echoechoechoechoecho")
                .formParam("scope", scope)
                .post(AUTH_URL);

        assertEquals(302, response.getStatusCode());
        Map<String, String> q = extractQuery(response.getHeader("Location"));
        assertTrue(q.containsKey("code"));
        assertTrue(q.get("code").length() > 0);
        return q.get("code");
    }

    @Test
    public void shouldGetTokenForOAuthAccessCode() throws Exception
    {
        String authCode = getCodeFromAuthorizationEndpoint("user.read");

        String tokenResponse = given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(CLIENTID, CLIENTSECRET))
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("client_id", CLIENTID)
                .formParam("grant_type", "authorization_code")
                .formParam("code", authCode)
                .post(TOKEN_URL).asString();

        String token = from(tokenResponse).get("access_token");
        assertNotNull(token);
        assertFalse(token.isEmpty());

        // trying the same auth code again should fail
        expect()
                .statusCode(400)
        .given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(CLIENTID, CLIENTSECRET))
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("client_id", CLIENTID)
                .formParam("grant_type", "authorization_code")
                .formParam("code", authCode)
                .post(TOKEN_URL);
    }

    @Test
    public void shouldGetTokenWithClientExpiryIfNoneRequested() throws Exception
    {
        String tokenResponse = given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(CLIENTID, CLIENTSECRET))
                .formParam("client_id", CLIENTID)
                .formParam("grant_type", "authorization_code")
                .formParam("code_type", "device_authorization")
                .formParam("code", GOOD_NONCE)
                .post(TOKEN_URL).asString();

        String token = from(tokenResponse).get("access_token");
        assertNotNull(token);

        assertNotNull(from(tokenResponse).get("expires_in"));
        long expiresIn = from(tokenResponse).getLong("expires_in");
        assertEquals(_clientRepository.findByClientId(CLIENTID).getExpireDuration(), expiresIn);
    }

    @Test
    public void shouldGetTokenIfRequestedExpiryIsLessThanClientExpiry() throws Exception
    {
        long requestedExpiresIn = 86400L;

        String tokenResponse = given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(CLIENTID, CLIENTSECRET))
                .formParam("client_id", CLIENTID)
                .formParam("grant_type", "authorization_code")
                .formParam("code_type", "device_authorization")
                .formParam("code", GOOD_NONCE)
                .formParam("expires_in", requestedExpiresIn)
                .post(TOKEN_URL).asString();

        String token = from(tokenResponse).get("access_token");
        assertNotNull(token);

        assertNotNull(from(tokenResponse).get("expires_in"));
        long expiresIn = from(tokenResponse).getLong("expires_in");
        assertEquals(requestedExpiresIn, expiresIn);
    }

    @Test
    public void shouldGetTokenWithClientExpiryRequested() throws Exception
    {
        String authCode = getCodeFromAuthorizationEndpoint("user.read");
        long requestedExpiresIn = _clientRepository.findByClientId(CLIENTID).getExpireDuration();

        String tokenResponse = given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(CLIENTID, CLIENTSECRET))
                .formParam("client_id", CLIENTID)
                .formParam("grant_type", "authorization_code")
                .formParam("code_type", "device_authorization")
                .formParam("code", GOOD_NONCE)
                .formParam("expires_in", requestedExpiresIn)
                .post(TOKEN_URL).asString();

        String token = from(tokenResponse).get("access_token");
        assertNotNull(token);

        assertNotNull(from(tokenResponse).get("expires_in"));
        long expiresIn = from(tokenResponse).getLong("expires_in");
        assertEquals(requestedExpiresIn, expiresIn);
    }

    @Test
    public void shouldFailToGetTokenIfRequestedExpiryIsLargerThanClientExpiry() throws Exception
    {
        String authCode = getCodeFromAuthorizationEndpoint("user.read");
        long requestedExpiresIn =
                _clientRepository.findByClientId(CLIENTSHORTEXPIRYID).getExpireDuration() + 9000;

        expect()
                .statusCode(400)
        .given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(CLIENTSHORTEXPIRYID, CLIENTSECRET))
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("client_id", CLIENTID)
                .formParam("grant_type", "authorization_code")
                .formParam("code_type", "device_authorization")
                .formParam("code", GOOD_NONCE)
                .formParam("expires_in", requestedExpiresIn)
                .post(TOKEN_URL).asString();
    }

    /** Positive test case but using client_secret form-param allowed by OAuth standard */
    @Test
    public void shouldSupportClientSecret() throws Exception
    {
        String response = given()
                .formParam("client_id", CLIENTID)
                .formParam("client_secret", CLIENTSECRET)
                .formParam("grant_type", "authorization_code")
                .formParam("code_type", "device_authorization")
                .formParam("code", GOOD_NONCE)
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
                .formParam("authorization_code", GOOD_NONCE)
                .formParam("redirect_uri", CLIENTREDIRECT)
            .post(TOKEN_URL);

        assertEquals(400, post.getStatusCode());
        assertEquals("unsupported_grant_type", from(post.asString()).get("error"));
    }

    @Test
    public void shouldUseDefaultClientScopesIfNoneProvidedWithSPNonce() throws Exception
    {
        String response = given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(CLIENTID, CLIENTSECRET))
                .formParam("client_id", CLIENTID)
                .formParam("grant_type", "authorization_code")
                .formParam("code_type", "device_authorization")
                .formParam("code", GOOD_NONCE)
                .post(TOKEN_URL).asString();

        String token = from(response).get("access_token");
        assertNotNull(token);
        assertFalse(token.isEmpty());

        String verifyResponse = given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(RESOURCEKEY, RESOURCESECRET))
                .queryParam("access_token", token)
                .get(TOKENINFO_URL).asString();

        ArrayList<String> scopes = from(verifyResponse).get("scopes");
        assertEquals(_clientRepository.findByClientId(CLIENTID).getScopes(), Sets.newHashSet(scopes));
    }

    @Test
    public void shouldUseScopesInRequestWithSPNonce() throws Exception
    {
        String response = given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(CLIENTID, CLIENTSECRET))
                .formParam("client_id", CLIENTID)
                .formParam("grant_type", "authorization_code")
                .formParam("code_type", "device_authorization")
                .formParam("code", GOOD_NONCE)
                .formParam("scope", "files.read,user.read")
                .post(TOKEN_URL).asString();

        String token = from(response).get("access_token");
        assertNotNull(token);
        assertFalse(token.isEmpty());

        String verifyResponse = given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(RESOURCEKEY, RESOURCESECRET))
                .queryParam("access_token", token)
                .get(TOKENINFO_URL).asString();

        ArrayList<String> scopes = from(verifyResponse).get("scopes");
        assertEquals(Sets.newHashSet("files.read", "user.read"), Sets.newHashSet(scopes));
    }

    @Test
    public void shouldUseScopesWithSOIDInRequestWithSPNonce() throws Exception
    {
        String response = given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(CLIENTID, CLIENTSECRET))
                .formParam("client_id", CLIENTID)
                .formParam("grant_type", "authorization_code")
                .formParam("code_type", "device_authorization")
                .formParam("code", GOOD_NONCE)
                .formParam("scope", "files.read:" + VALID_SOID + ",user.read")
                .post(TOKEN_URL).asString();

        String token = from(response).get("access_token");
        assertNotNull(token);
        assertFalse(token.isEmpty());

        String verifyResponse = given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(RESOURCEKEY, RESOURCESECRET))
                .queryParam("access_token", token)
                .get(TOKENINFO_URL).asString();

        ArrayList<String> scopes = from(verifyResponse).get("scopes");
        assertEquals(Sets.newHashSet("files.read:" + VALID_SOID, "user.read"),
                Sets.newHashSet(scopes));
    }

    @Test
    public void shouldIgnoreScopesInRequestWithAuthCode() throws Exception
    {
        String authCode = getCodeFromAuthorizationEndpoint("files.read");

        String tokenResponse = given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(CLIENTID, CLIENTSECRET))
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("client_id", CLIENTID)
                .formParam("grant_type", "authorization_code")
                .formParam("code", authCode)
                .formParam("scope", "files.read,files.write,user.read,user.write,organization.admin")
                .post(TOKEN_URL).asString();

        String token = from(tokenResponse).get("access_token");
        assertNotNull(token);
        assertFalse(token.isEmpty());

        String verifyResponse = given()
                .header(HttpHeaders.Names.AUTHORIZATION, buildBasicAuthHeader(RESOURCEKEY, RESOURCESECRET))
                .queryParam("access_token", token)
                .get(TOKENINFO_URL).asString();

        // ensure token has the scope that was authorized by the user
        ArrayList<String> scopes = from(verifyResponse).get("scopes");
        assertEquals(Sets.newHashSet("files.read"), Sets.newHashSet(scopes));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void shouldListTokensOnGet() throws Exception
    {
        Response response = givenServiceAuthorizedClient()
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
        final String tokenVal = createTokenForUser(false);

        expect()
                .statusCode(200)
        .given()
                .delete(TOKEN_URL + "/" + tokenVal);

        assertNull(_accessTokenRepository.findByToken(tokenVal));
    }

    @Test
    public void deleteAllTokens_shouldSucceedForUser() throws Exception
    {
        assertFalse("tokens exist", _accessTokenRepository.findByOwner(USERNAME).isEmpty());

        givenServiceAuthorizedClient()
        .expect()
                .statusCode(200)
        .when()
                .delete(USERS_URL + "/" + USERNAME + "/tokens");

        assertTrue("deleted them", _accessTokenRepository.findByOwner(USERNAME).isEmpty());
    }

    @Test
    public void deleteAllTokens_shouldSucceedForDelegates() throws Exception
    {
        _accessTokenRepository.deleteAllTokensByOwner(USERNAME);

        createTokenForUser(true);
        createTokenForUser(false);

        assertEquals("2 tokens", 2, _accessTokenRepository.findByOwner(USERNAME).size());

        givenServiceAuthorizedClient()
        .expect()
                .statusCode(200)
        .when()
                .delete(USERS_URL + "/" + USERNAME + "/delegates");

        assertEquals("1 tokens", 1, _accessTokenRepository.findByOwner(USERNAME).size());
    }

    private String createTokenForUser(boolean isAdmin) throws ExInvalidID
    {
        String tokenVal = String.valueOf(Math.random());

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(USERNAME);
        principal.setEffectiveUserID(UserID.fromExternal(isAdmin ? ":2" : USERNAME));
        principal.setOrganizationID(OrganizationID.PRIVATE_ORGANIZATION);
        AccessToken token = new AccessToken(
                tokenVal,
                principal,
                _clientRepository.findByClientId(CLIENTID),
                0,
                Sets.newHashSet("read"),
                "");
        _accessTokenRepository.save(token);
        _session.flush();
        return tokenVal;
    }

    @Test
    public void deleteAllTokens_shouldRequireOwner() throws Exception
    {
        givenServiceAuthorizedClient()
        .expect()
                .statusCode(400)
        .when()
                .delete(USERS_URL);
    }

    @Test
    public void deleteAllTokens_shouldSucceedIfNoWorkPerformed() throws Exception
    {
        givenServiceAuthorizedClient()
        .expect()
                .statusCode(200)
        .when()
                .delete(USERS_URL + "/" + "joe@example.com" + "/tokens");
    }

    private String buildBasicAuthHeader(String user, String password)
    {
        String mashup = user + ":" + password;
        return "Basic " + Base64.encodeBytes(mashup.getBytes());
    }

    // Ideally we'd use delegated-user auth, but that requires that bifrost be able to know that e.g.
    // yuri@aerofs.com is allowed to access drew@aerofs.com's tokens.
    private RequestSpecification givenServiceAuthorizedClient()
    {
        return given()
                .header(HttpHeaders.Names.AUTHORIZATION, AeroService.getHeaderValue("bunker", testDeploymentSecret()));
    }
}
