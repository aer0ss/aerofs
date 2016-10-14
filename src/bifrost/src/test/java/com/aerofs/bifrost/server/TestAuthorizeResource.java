package com.aerofs.bifrost.server;

import com.aerofs.base.ex.ExExternalAuthFailure;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.bifrost.oaaas.auth.NonceChecker.AuthorizedClient;
import com.aerofs.bifrost.oaaas.model.AuthorizationRequest;
import com.aerofs.ids.UserID;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.jayway.restassured.response.Response;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.URL;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

/**
 */
public class TestAuthorizeResource extends BifrostTest
{
    private static final String ADMIN_NONCE = "nonce-nonce-revolution";
    private static final String USER_NONCE = "nonce-ponder-tie-em";
    private static final String BAD_NONCE = "dies-ist-nicht-eine-nonce";

    private static final String VALID_SOID =
            "df084c5033083b540e7730a2b29d928b457b7a71fbd74d86add7c4f0a56d2093";
    private static final String VALID_SOID_1 =
            "df084c5033083b540e7730a2b29d928b86a457dfdd600978a84a830c72acdad1";

    @Before
    public void setUpTestAuthorizeResource() throws Exception
    {
        when(_nonceChecker.authorizeAPIClient(eq(ADMIN_NONCE), anyString())).thenReturn(
                new AuthorizedClient(UserID.fromExternal("test1@b.c"), OrganizationID.PRIVATE_ORGANIZATION, true));

        when(_nonceChecker.authorizeAPIClient(eq(BAD_NONCE), anyString())).thenThrow(
                new ExExternalAuthFailure());
    }

    @Test
    public void testShouldGet400IfMissingClientId()
    {
        expect()
                .statusCode(400)
        .given()
                .formParam("response_type", "code")
                .formParam("nonce", ADMIN_NONCE)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("scope", "user.read")
                .post(AUTH_URL);
    }

    @Test
    public void testShouldGet400IfInvalidClientId()
    {
        expect()
                .statusCode(400)
        .given()
                .formParam("client_id", "nothing-to-see-here-folks")
                .formParam("response_type", "code")
                .formParam("nonce", ADMIN_NONCE)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("scope", "user.read")
                .post(AUTH_URL);
    }

    @Test
    public void testShouldGet400IfMissingRedirectUri()
    {
        expect()
                .statusCode(400)
        .given()
                .formParam("client_id", CLIENTID)
                .formParam("response_type", "code")
                .formParam("nonce", ADMIN_NONCE)
                .formParam("scope", "user.read")
                .post(AUTH_URL);
    }

    @Test
    public void testShouldGet400IfRedirectUriDoesNotMatchDb()
    {
        // the redirect_uri should match the one that the site admin specified
        // when registering the client
        expect()
                .statusCode(400)
        .given()
                .formParam("client_id", CLIENTID)
                .formParam("response_type", "code")
                .formParam("nonce", ADMIN_NONCE)
                .formParam("redirect_uri", "http://www.malicious.com/passwordstealer.php")
                .formParam("scope", "user.read")
                .post(AUTH_URL);
    }

    @Test
    public void testShouldRedirectErrorIfNonceIsMissing()
    {
        expect()
                .statusCode(302).and()
                .response().header("Location", startsWith(CLIENTREDIRECT)).and()
                .response().header("Location", containsString("error=invalid_request"))
        .given()
                .formParam("response_type", "code")
                .formParam("client_id", CLIENTID)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("scope", "user.read")
                .post(AUTH_URL);
    }

    @Test
    public void testShouldRedirectErrorIfNonceIsInvalid()
    {
        expect()
                .statusCode(302).and()
                .response().header("Location", startsWith(CLIENTREDIRECT)).and()
                .response().header("Location", containsString("error=invalid_request"))
        .given()
                .formParam("response_type", "code")
                .formParam("client_id", CLIENTID)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("nonce", BAD_NONCE)
                .formParam("scope", "user.read")
                .post(AUTH_URL);
    }

    @Test
    public void testShouldRedirectErrorIfResponseTypeIsMissing()
    {
        expect()
                .statusCode(302).and()
                .response().header("Location", startsWith(CLIENTREDIRECT)).and()
                .response().header("Location", containsString("error=invalid_request"))
        .given()
                .formParam("client_id", CLIENTID)
                .formParam("nonce", ADMIN_NONCE)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("scope", "user.read")
                .post(AUTH_URL);
    }

    @Test
    public void testShouldRedirectErrorIfScopeTypeIsMissing()
    {
        expect()
                .statusCode(302).and()
                .response().header("Location", startsWith(CLIENTREDIRECT)).and()
                .response().header("Location", containsString("error=invalid_request"))
        .given()
                .formParam("response_type", "code")
                .formParam("client_id", CLIENTID)
                .formParam("nonce", ADMIN_NONCE)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .post(AUTH_URL);
    }

    @Test
    public void testShouldRedirectErrorIfResponseTypeIsInvalid()
    {
        expect()
                .statusCode(302).and()
                .response().header("Location", startsWith(CLIENTREDIRECT)).and()
                .response().header("Location", containsString("error=unsupported_response_type"))
        .given()
                .formParam("response_type", "interpretive_dance")
                .formParam("client_id", CLIENTID)
                .formParam("nonce", ADMIN_NONCE)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("scope", "user.read")
                .post(AUTH_URL);
    }

    @Test
    public void testShouldRedirectErrorWithState()
    {
        // missing response_type should trigger error
        expect()
                .statusCode(302).and()
                .response().header("Location", startsWith(CLIENTREDIRECT)).and()
                .response().header("Location", containsString("state=echoechoechoechoecho"))
        .given()
                .formParam("client_id", CLIENTID)
                .formParam("nonce", ADMIN_NONCE)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("state", "echoechoechoechoecho")
                .formParam("scope", "user.read")
                .post(AUTH_URL);
    }

    @Test
    public void testShouldRedirectAuthCodeOnSuccess()
    {
        expect()
                .statusCode(302)
                .response().header("Location", startsWith(CLIENTREDIRECT)).and()
                .response().header("Location", containsString("state=echoechoechoechoecho"))
                .response().header("Location", containsString("code="))
        .given()
                .formParam("response_type", "code")
                .formParam("client_id", CLIENTID)
                .formParam("nonce", ADMIN_NONCE)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("state", "echoechoechoechoecho")
                .formParam("scope", "user.read")
                .post(AUTH_URL);
    }

    @Test
    public void testShouldAllowAdminRequest() throws Exception
    {
        Response response =
                given().formParam("response_type", "code")
                        .formParam("client_id", CLIENTID)
                        .formParam("nonce", ADMIN_NONCE)
                        .formParam("redirect_uri", CLIENTREDIRECT)
                        .formParam("state", "wang_chung")
                        .formParam("scope", "organization.admin") // FIXME : magic string constant
                .post(AUTH_URL);

        Assert.assertEquals(302, response.getStatusCode());
        assertTrue(response.header("Location").startsWith(CLIENTREDIRECT));
        assertTrue(response.header("Location").contains("state=wang_chung"));
        assertTrue(response.header("Location").contains("code="));

        String code = "woops";
        for (String clause : new URL(response.header("Location")).getQuery().split("&")) {
            if (clause.startsWith("code=")) {
                code = clause.substring(5);
            }
        }

        AuthorizationRequest authRequest = _authRequestDb.findByAuthCode(code);
        authRequest.decodePrincipal();
        AuthenticatedPrincipal principal = authRequest.getPrincipal();
        assertTrue(principal.getEffectiveUserID().isTeamServerID());
    }

    @Test
    public void testShouldDisallowUserRequestForAdmin()
    {
        expect()
                .statusCode(302)
                .response().header("Location", startsWith(CLIENTREDIRECT)).and()
                .response().header("Location", containsString("state=wang_chung"))
                .response().header("Location", containsString("error_description=proof-of-identity+nonce+is+invalid"))
        .given()
                .formParam("response_type", "code")
                .formParam("client_id", CLIENTID)
                .formParam("nonce", USER_NONCE)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("state", "wang_chung")
                .formParam("scope", "organization.admin") // FIXME : magic string constant
                .post(AUTH_URL);
    }

    @Test
    public void testShouldDisallowScopeStringWithInvalidScope()
    {
        expect()
                .statusCode(302).and()
                .response().header("Location", startsWith(CLIENTREDIRECT)).and()
                .response().header("Location", containsString("error=invalid_request"))
        .given()
                .formParam("response_type", "code")
                .formParam("client_id", CLIENTID)
                .formParam("nonce", ADMIN_NONCE)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("scope", "user.read,this.is.not.a.scope,user.write")
                .post(AUTH_URL);
    }

    @Test
    public void testShouldDisallowBadlyFormedScopeString()
    {
        expect()
                .statusCode(302).and()
                .response().header("Location", startsWith(CLIENTREDIRECT)).and()
                .response().header("Location", containsString("error=invalid_request"))
        .given()
                .formParam("response_type", "code")
                .formParam("client_id", CLIENTID)
                .formParam("nonce", ADMIN_NONCE)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("scope", "user.read,,user.write")
                .post(AUTH_URL);
    }

    @Test
    public void testShouldAllowScopeWithSOID()
    {
        expect()
                .statusCode(302)
                .response().header("Location", startsWith(CLIENTREDIRECT)).and()
                .response().header("Location", containsString("code="))
        .given()
                .formParam("response_type", "code")
                .formParam("client_id", CLIENTID)
                .formParam("nonce", ADMIN_NONCE)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("scope", "files.read:" + VALID_SOID)
                .post(AUTH_URL);
    }

    @Test
    public void testShouldDisallowScopeWithBadSOID()
    {
        expect()
                .statusCode(302).and()
                .response().header("Location", startsWith(CLIENTREDIRECT)).and()
                .response().header("Location", containsString("error=invalid_request"))
        .given()
                .formParam("response_type", "code")
                .formParam("client_id", CLIENTID)
                .formParam("nonce", ADMIN_NONCE)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("scope", "files.read:thisisnotavalidsoid")
                .post(AUTH_URL);
    }

    @Test
    public void testShouldAllowScopeWithTwoSOIDs()
    {
        expect()
                .statusCode(302)
                .response().header("Location", startsWith(CLIENTREDIRECT)).and()
                .response().header("Location", containsString("code="))
        .given()
                .formParam("response_type", "code")
                .formParam("client_id", CLIENTID)
                .formParam("nonce", ADMIN_NONCE)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("scope", "files.read:" + VALID_SOID + ",files.read:" + VALID_SOID_1)
                .post(AUTH_URL);
    }
}
