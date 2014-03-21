package com.aerofs.bifrost.server;

import com.aerofs.base.ex.ExExternalAuthFailure;
import com.aerofs.bifrost.module.AuthorizationRequestDAO;
import com.aerofs.bifrost.oaaas.model.AuthorizationRequest;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.proto.Sp.AuthorizeMobileDeviceReply;
import com.jayway.restassured.response.Response;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import java.net.MalformedURLException;
import java.net.URL;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 */
public class TestAuthorizeResource extends BifrostTest
{
    private static final String ADMIN_NONCE = "nonce-nonce-revolution";
    private static final String USER_NONCE = "nonce-ponder-tie-em";
    private static final String BAD_NONCE = "dies-ist-nicht-eine-nonce";

    @Before
    public void setUpTestAuthorizeResource() throws Exception
    {
        when(_spClient.authorizeMobileDevice(eq(ADMIN_NONCE), anyString())).thenReturn(
                AuthorizeMobileDeviceReply.newBuilder()
                        .setUserId("test1@b.c")
                        .setOrgId("2")
                        .setIsOrgAdmin(true)
                        .build());

        when(_spClient.authorizeMobileDevice(eq(BAD_NONCE), anyString())).thenThrow(
                new ExExternalAuthFailure()
        );
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
        assertTrue(principal.getUserID().isTeamServerID());
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
}
