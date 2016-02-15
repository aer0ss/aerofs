/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.ex.ExExternalAuthFailure;
import com.aerofs.lib.LibParam.Identity;
import com.aerofs.lib.LibParam.Identity.Authenticator;
import com.aerofs.lib.LibParam.OpenId;
import com.aerofs.lib.LibParam.REDIS;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.testlib.AbstractBaseTest;
import com.dyuproject.openid.Constants;
import com.dyuproject.openid.Constants.Mode;
import com.dyuproject.openid.OpenIdContext;
import com.dyuproject.openid.OpenIdUser;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.RestAssuredConfig;
import com.jayway.restassured.response.Response;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Spy;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.RedirectConfig.redirectConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Some Notes:
 * - We extend AbstractTest solely to be able to toggle whether OpenId is enabled or not.
 * - This class doesn't test the parts of OpenId in SP _at all_.
 * - This class doesn't test interactions between IdentityServlet and SPLifecycleListener because
 *   currently the only reason IdentityServlet needs SPLifecycleListener is to intialize
 *   configuration.
 */
public class TestIdentityServlet extends AbstractBaseTest
{
    protected final int NONCE_LIFETIME_SECS = 300;
    protected final String SERVLET_URI = "http://localhost";
    protected final String USER_EMAIL = "john@doe.com";
    protected final String USER_FIRSTNAME = "john";
    protected final String USER_LASTNAME = "doe";

    protected int _port;
    protected IdentitySessionManager _identitySessionManager;
    protected Server _server;
    protected String _delegate;
    protected String _session;

    @Spy protected DumbAssociation _association;

    /**
     * This function fakes signing in the test user with the IDP if shouldSucceed is true, otherwise
     * it fakes that the user isn't signed in.
     *
     * @param redirect The redirect that the IdentityServlet issued to the IDP.
     * @param delegateNonce The delegate nonce to use.
     * @param shouldSucceed Whether or not to sign in the test user.
     * @return The response from the IDP.
     * @throws Exception
     */
    void fakeSignInAndSendAuthResponse(Response redirect, String delegateNonce,
            Boolean shouldSucceed) throws Exception
    {
        String redirectUrl = redirect.getHeader("Location");
        Map<String, String> redirectParams = parseUrlParameters(redirectUrl);

        // Association will succeed or not depending on shouldSucceed.
        doReturn(shouldSucceed).when(_association).verifyAuth(any(OpenIdUser.class),
                anyMapOf(String.class, String.class), any(OpenIdContext.class));

        given() .param("openid.ns", "http://specs.openid.net/auth/2.0")
                .param(Constants.OPENID_MODE, Mode.ID_RES) // openid.mode
                .param("openid.op_endpoint", OpenId.ENDPOINT_URL)
                .param("openid.claimed_id", redirectParams.get("openid.claimed_id"))
                .param(OpenId.IDP_USER_ATTR, redirectParams.get(OpenId.IDP_USER_ATTR)) // openid.identity
                .param("openid.return_to", redirectParams.get("openid.return_to"))
                .param("openid.response_nonce", String.valueOf(System.currentTimeMillis()))
                .param("openid.assoc_handle", "unnecessary because we spy DumbAssociation")
                .param("openid.signed", "unnecessary because we spy DumbAssociation")
                .param("openid.sig", "unnecessary because we spy DumbAssociation")
                // Parameters not in specification.
                .param(OpenId.IDP_USER_EMAIL, USER_EMAIL)
                .param(OpenId.IDP_USER_FIRSTNAME, USER_FIRSTNAME)
                .param(OpenId.IDP_USER_LASTNAME, USER_LASTNAME)
                .param(OpenId.OPENID_DELEGATE_NONCE, delegateNonce). // sp.nonce
        get(OpenId.IDENTITY_RESP_PATH);
    }

    @BeforeClass
    public static void setUpOnce()
    {
        Properties properties = new Properties();
        properties.setProperty("openid.service.enabled", "true");
        properties.setProperty("openid.service.timeout", "30");
        properties.setProperty("openid.service.session.timeout", "30");
        properties.setProperty("openid.service.session.interval", "1");
        properties.setProperty("openid.service.url", "");
        properties.setProperty("openid.service.realm", "https://*.fakerealm.com");
        properties.setProperty("openid.idp.endpoint.url", "https://www.fakeendpoint.com");
        properties.setProperty("openid.idp.user.uid.attribute", "openid.identity");
        properties.setProperty("openid.idp.user.uid.pattern", "");
        properties.setProperty("openid.idp.user.extension", "ax");
        properties.setProperty("openid.idp.user.email", "openid.ext1.value.email");
        properties.setProperty("openid.idp.user.user.name.first", "openid.ext1.value.firstname");
        properties.setProperty("openid.idp.user.user.name.last", "openid.ext1.value.lastname");
        ConfigurationProperties.setProperties(properties);
    }

    @Before
    public void setUp() throws Exception
    {
        Identity.AUTHENTICATOR = Authenticator.OPENID;

        PooledJedisConnectionProvider jedis = new PooledJedisConnectionProvider();
        jedis.init_(REDIS.AOF_ADDRESS.getHostName(), REDIS.AOF_ADDRESS.getPort(), REDIS.PASSWORD);

        _identitySessionManager = new IdentitySessionManager(jedis);
        _server = setUpServer();
        _port = (_server.getConnectors()[0]).getLocalPort();
        _delegate = null;
        _session = null;

        RestAssured.baseURI = SERVLET_URI;
        RestAssured.port = _port;
        RestAssured.config = new RestAssuredConfig()
                                 .redirect(redirectConfig().followRedirects(false));
    }

    @After
    public void tearDown() throws Exception
    {
        Identity.AUTHENTICATOR = Authenticator.LOCAL_CREDENTIAL;

        _server.stop();
        _server.destroy();
        _identitySessionManager._jedisConProvider.getConnection().flushDB();
    }

    /* Miscellaneous Tests */
    @Test
    public void shouldErrorWithOpenIdDisabled() throws Exception
    {
        Identity.AUTHENTICATOR = Authenticator.LOCAL_CREDENTIAL;

        expect().statusCode(405).
        when()  .get("");
    }

    @Test
    public void shouldErrorWithIncorrectPath() throws Exception
    {
        expect().statusCode(404).
        when()  .get("/badpath");

        expect().statusCode(404).
        when()  .get("");
    }

    /* authRequest Tests */
    @Test
    public void shouldRedirectForNormalDelegateNonce() throws Exception
    {
        given() .param(OpenId.IDENTITY_REQ_PARAM, "209a2a38405f493bb1729aa32d801009").
        expect().statusCode(302).
        when()  .get(OpenId.IDENTITY_REQ_PATH);
    }
    @Test
    public void shouldReturnEmptyForNoDelegateNonce() throws Exception
    {
        expect().statusCode(200).
        when()  .get(OpenId.IDENTITY_REQ_PATH);
    }

    /* Whole Servlet Tests */
    @Test
    public void replaysAttackShouldFail() throws Exception
    {
        shouldWorkWithValidDelegateNonce();

        // Session nonce replay.
        try {
            _identitySessionManager.getSession(_session);
            fail("Expected exception");
        } catch (ExExternalAuthFailure e) { /* pass */ }

        // Delegate nonce replay.
        Response authRequestResponse = given().param(OpenId.IDENTITY_REQ_PARAM, _delegate)
                                              .get(OpenId.IDENTITY_REQ_PATH);
        fakeSignInAndSendAuthResponse(authRequestResponse, _delegate, true);
        try {
            _identitySessionManager.getSession(_session);
            fail("Expected exception");
        } catch (ExExternalAuthFailure e) { /* pass */ }
    }

    @Test
    public void shouldFailIfUserIsntAuthenticated() throws Exception
    {
        _session = _identitySessionManager.createSession(NONCE_LIFETIME_SECS);
        _delegate = _identitySessionManager.createDelegate(_session, NONCE_LIFETIME_SECS);
        // Make sure session nonce exists and isn't authenticated.
        assertNull(_identitySessionManager.getSession(_session));

        Response authRequestResponse = given().param(OpenId.IDENTITY_REQ_PARAM, _delegate)
                                              .get(OpenId.IDENTITY_REQ_PATH);

        fakeSignInAndSendAuthResponse(authRequestResponse, _delegate, false);

        assertNull(_identitySessionManager.getSession(_session));
    }

    @Test
    public void shouldFailWithNonExistantNonces() throws Exception
    {
        _session = "bad session nonce";
        _delegate = "bad delegate nonce";

        try {
            _identitySessionManager.getSession(_session);
            fail("Expected exception");
        } catch (ExExternalAuthFailure e) { /* pass */ }

        Response authRequestResponse = given().param(OpenId.IDENTITY_REQ_PARAM, _delegate)
                                              .get(OpenId.IDENTITY_REQ_PATH);

        fakeSignInAndSendAuthResponse(authRequestResponse, _delegate, false);

        try {
            _identitySessionManager.getSession(_session);
            fail("Expected exception");
        } catch (ExExternalAuthFailure e) { /* pass */ }
    }

    @Test
    public void shouldWorkWithValidDelegateNonce() throws Exception
    {
        _session = _identitySessionManager.createSession(NONCE_LIFETIME_SECS);
        _delegate = _identitySessionManager.createDelegate(_session, NONCE_LIFETIME_SECS);
        // Make sure session nonce exists and isn't authenticated.
        assertNull(_identitySessionManager.getSession(_session));

        Response authRequestResponse = given().param(OpenId.IDENTITY_REQ_PARAM, _delegate)
                                              .get(OpenId.IDENTITY_REQ_PATH);

        fakeSignInAndSendAuthResponse(authRequestResponse, _delegate, true);

        IdentitySessionAttributes sessionAttributes = _identitySessionManager.getSession(_session);
        assertEquals(USER_EMAIL, sessionAttributes.getEmail());
        assertEquals(USER_FIRSTNAME, sessionAttributes.getFirstName());
        assertEquals(USER_LASTNAME, sessionAttributes.getLastName());
    }

    /* Helper Functions */
    protected Map<String, String> parseUrlParameters(String url)
    {
        Map<String, String> redirectParams = new HashMap<String, String>();

        URI locationUri = URI.create(url);
        List<NameValuePair> redirectParamsList = URLEncodedUtils.parse(locationUri, "UTF-8");
        for (NameValuePair param : redirectParamsList) {
            redirectParams.put(param.getName(), param.getValue());
        }

        return redirectParams;
    }

    protected Server setUpServer() throws Exception
    {
        IdentityServlet servlet = spy(new IdentityServlet());
        doReturn(_association).when(servlet).makeAssociation();

        Server server = new Server(0);
        Context root = new Context(server, "/", Context.SESSIONS);
        root.addServlet(new ServletHolder(servlet), "/*");
        server.start();

        return server;
    }
}

