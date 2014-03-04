package com.aerofs.bifrost.server;

import com.aerofs.base.ex.ExExternalAuthFailure;
import com.aerofs.proto.Sp.AuthorizeMobileDeviceReply;
import org.junit.Before;
import org.junit.Test;

import static com.jayway.restassured.RestAssured.expect;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

/**
 */
public class TestAuthorizeResource extends BifrostTest
{
    private static final String GOODNONCE = "nonce-nonce-revolution";
    private static final String BADNONCE = "dies-ist-nicht-eine-nonce";

    @Before
    public void setUpTestAuthorizeResource() throws Exception
    {
        when(_spClient.authorizeMobileDevice(eq(GOODNONCE), anyString())).thenReturn(
                AuthorizeMobileDeviceReply.newBuilder()
                        .setUserId("test1@b.c")
                        .setOrgId("2")
                        .setIsOrgAdmin(true)
                        .build());

        when(_spClient.authorizeMobileDevice(eq(BADNONCE), anyString())).thenThrow(
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
                .formParam("nonce", GOODNONCE)
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
                .formParam("nonce", GOODNONCE)
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
                .formParam("nonce", GOODNONCE)
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
                .formParam("nonce", GOODNONCE)
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
                .formParam("nonce", BADNONCE)
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
                .formParam("nonce", GOODNONCE)
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
                .formParam("nonce", GOODNONCE)
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
                .formParam("nonce", GOODNONCE)
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
                .formParam("nonce", GOODNONCE)
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
                .formParam("nonce", GOODNONCE)
                .formParam("redirect_uri", CLIENTREDIRECT)
                .formParam("state", "echoechoechoechoecho")
                .formParam("scope", "user.read")
                .post(AUTH_URL);
    }
}
