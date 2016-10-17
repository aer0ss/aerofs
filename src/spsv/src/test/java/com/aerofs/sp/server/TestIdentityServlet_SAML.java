package com.aerofs.sp.server;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.Util;
import com.aerofs.proto.Sp;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.sp.server.integration.AbstractSPTest;
import com.google.common.collect.Lists;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.RestAssuredConfig;
import com.jayway.restassured.response.Response;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.*;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.core.impl.AuthnRequestImpl;
import org.opensaml.ws.message.decoder.MessageDecodingException;
import org.opensaml.xml.Configuration;
import org.opensaml.xml.io.Unmarshaller;
import org.opensaml.xml.io.UnmarshallerFactory;
import org.opensaml.xml.util.Base64;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.RedirectConfig.redirectConfig;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.spy;

public class TestIdentityServlet_SAML extends AbstractSPTest {

    private static final String HOST = "unit.testfs.com";
    private static final String SAML_IDP_HOST = "https://www.testsaml.com";
    private static final String FIRST = "Saml";
    private static final String LAST = "Unittest";
    private static final String EMAIL = "samlunittester@gmail.com";
    private static Properties properties;

    protected final int NONCE_LIFETIME_SECS = 300;
    protected final String SERVLET_URI = "http://localhost";

    protected int _port;
    protected IdentitySessionManager _identitySessionManager;
    protected Server _server;
    protected String _delegate;
    protected String _session;

    @BeforeClass
    public static void setUpOnce() throws IOException
    {
        properties = new Properties();
        properties.setProperty("lib.authenticator", "saml");
        properties.setProperty("base.host.unified", HOST);
        properties.setProperty("saml.idp.host", SAML_IDP_HOST);
        StringBuilder sb = new StringBuilder();

        try (BufferedReader br = new BufferedReader(new FileReader(Util.join(System.getProperty("user.dir"),
                "src/test/java/com/aerofs/sp/server/saml_resources/saml_idp_x509_cert")))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line + "\n");
            }
        }
        properties.setProperty("saml.idp.x509.certificate", sb.toString());
        properties.setProperty("openid.service.timeout", "30");
        properties.setProperty("openid.service.session.timeout", "30");
        properties.setProperty("openid.service.session.interval", "1");
        ConfigurationProperties.setProperties(properties);
    }

    @Before
    public void setUp() throws Exception
    {
        ConfigurationProperties.setProperties(properties);
        LibParam.Identity.AUTHENTICATOR = LibParam.Identity.Authenticator.SAML;

        PooledJedisConnectionProvider jedis = new PooledJedisConnectionProvider();
        jedis.init_(LibParam.REDIS.AOF_ADDRESS.getHostName(), LibParam.REDIS.AOF_ADDRESS.getPort(), LibParam.REDIS.PASSWORD);

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
        LibParam.Identity.AUTHENTICATOR = LibParam.Identity.Authenticator.LOCAL_CREDENTIAL;

        _server.stop();
        _server.destroy();
        _identitySessionManager._jedisConProvider.getConnection().flushDB();
    }

    protected Server setUpServer() throws Exception
    {
        IdentityServlet servlet = spy(new IdentityServlet());

        Server server = new Server(0);
        Context root = new Context(server, "/", Context.SESSIONS);
        root.addServlet(new ServletHolder(servlet), "/*");
        server.start();

        return server;
    }

    private AuthnRequestImpl samlRequestDecoder(String request) throws MessageDecodingException
    {
        byte[] decoded  = Base64.decode(request);
        try {
            ByteArrayInputStream bytesIn = new ByteArrayInputStream(decoded);
            InflaterInputStream inflater = new InflaterInputStream(bytesIn, new Inflater(true));
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = docBuilder.parse(inflater);
            Element element = document.getDocumentElement();
            UnmarshallerFactory unmarshallerFactory = Configuration.getUnmarshallerFactory();
            Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(element);
            return (AuthnRequestImpl) unmarshaller.unmarshall(element);
        } catch (Exception e) {
            throw new MessageDecodingException("Unable to Base64 decode and inflate SAML message", e);
        }
    }


    @Test
    public void shouldCreateValidAuthenticatonRequest() throws Exception
    {
        _session = _identitySessionManager.createSession(NONCE_LIFETIME_SECS);
        _delegate = _identitySessionManager.createDelegate(_session, NONCE_LIFETIME_SECS);
        // Make sure session nonce exists and isn't authenticated.
        assertNull(_identitySessionManager.getSession(_session));

        Response authRequestResponse = given().param(LibParam.Identity.IDENTITY_REQ_PARAM, _delegate)
                .get(LibParam.Identity.IDENTITY_REQ_PATH);

        assertEquals(302, authRequestResponse.statusCode());
        URI uri = new URI(authRequestResponse.getHeader("Location"));
        assertEquals("www.testsaml.com", uri.getHost());

        List<NameValuePair> params = URLEncodedUtils.parse(uri, "UTF-8");
        assertEquals(2, params.size());
        Lists.newArrayList("SAMLRequest", "RelayState");
        assertThat(Arrays.asList(params.get(0).getName(), params.get(1).getName()),
                is(Arrays.asList("SAMLRequest", "RelayState")));

        int samlReqIndex = params.get(0).getName().equals("SAMLRequest") ? 0 : 1;
        AuthnRequestImpl authnRequest = samlRequestDecoder(params.get(samlReqIndex).getValue());
        assertEquals("https://unit.testfs.com/identity/os", authnRequest.getAssertionConsumerServiceURL());
        assertEquals(SAML_IDP_HOST, authnRequest.getDestination());
        assertEquals(SAMLConstants.SAML2_POST_BINDING_URI, authnRequest.getProtocolBinding());
        assertEquals(HOST + "_saml", authnRequest.getIssuer().getValue());

        int relayStateIndex = params.get(0).getName().equals("RelayState") ? 0 : 1;
        assertEquals(_delegate + " files" , params.get(relayStateIndex).getValue());
    }

    @Test
    public void shouldParseValidAssertion() throws Exception
    {

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(Util.join(System.getProperty("user.dir"),
                "src/test/java/com/aerofs/sp/server/saml_resources/saml_test_assertion.txt")))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }

        String testAssertion = sb.toString();

        _session = _identitySessionManager.createSession(NONCE_LIFETIME_SECS);
        _delegate = _identitySessionManager.createDelegate(_session, NONCE_LIFETIME_SECS);

        given()
                .param("SAMLResponse", testAssertion)
                .param("RelayState", _delegate + " https://unit.testfs.com")
                .post(LibParam.Identity.IDENTITY_RESP_PATH);

        Sp.ExtAuthSessionAttributes returnedAttributes = service.extAuthGetSessionAttributes(_session).get();
        assertEquals(FIRST, returnedAttributes.getFirstName());
        assertEquals(LAST, returnedAttributes.getLastName());
        assertEquals(EMAIL, returnedAttributes.getUserId());
    }

    @Test
    public void should400IfUnverifiedSignature() throws Exception
    {
        Properties properties = new Properties();
        properties.setProperty("lib.authenticator", "saml");
        properties.setProperty("saml.idp.x509.certificate", "lolcert");
        ConfigurationProperties.setProperties(properties);
        rebuildSPService();

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(Util.join(System.getProperty("user.dir"),
                "src/test/java/com/aerofs/sp/server/saml_resources/saml_test_assertion.txt")))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }

        String testAssertion = sb.toString();

        _session = _identitySessionManager.createSession(NONCE_LIFETIME_SECS);
        _delegate = _identitySessionManager.createDelegate(_session, NONCE_LIFETIME_SECS);

        Response response = given()
                .param("SAMLResponse", testAssertion)
                .param("RelayState", _delegate + " https://unit.testfs.com")
                .post(LibParam.Identity.IDENTITY_RESP_PATH);
        assertEquals(500, response.statusCode());
    }

    @Test
    public void should403IfInvalidNonces() throws Exception
    {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(Util.join(System.getProperty("user.dir"),
                "src/test/java/com/aerofs/sp/server/saml_resources/saml_test_assertion.txt")))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }

        String testAssertion = sb.toString();

        _session = _identitySessionManager.createSession(NONCE_LIFETIME_SECS);
        _delegate = _identitySessionManager.createDelegate(_session, NONCE_LIFETIME_SECS);

        Response response = given()
                .param("SAMLResponse", testAssertion)
                .param("RelayState",  "123123123123 https://unit.testfs.com")
                .post(LibParam.Identity.IDENTITY_RESP_PATH);
        assertEquals(403, response.statusCode());
    }

    @Test
    public void should400IfNoEmailInSamlAssertion() throws Exception
    {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(Util.join(System.getProperty("user.dir"),
                "src/test/java/com/aerofs/sp/server/saml_resources/saml_test_assertion_no_email.txt")))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }

        String testAssertion = sb.toString();

        _session = _identitySessionManager.createSession(NONCE_LIFETIME_SECS);
        _delegate = _identitySessionManager.createDelegate(_session, NONCE_LIFETIME_SECS);

        Response response = given()
                .param("SAMLResponse", testAssertion)
                .param("RelayState",  _delegate + " https://unit.testfs.com")
                .post(LibParam.Identity.IDENTITY_RESP_PATH);
        assertEquals(400, response.statusCode());
    }

    @Test
    public void should400IfNoSAMLResponseParam() throws Exception
    {
        _session = _identitySessionManager.createSession(NONCE_LIFETIME_SECS);
        _delegate = _identitySessionManager.createDelegate(_session, NONCE_LIFETIME_SECS);

        Response response = given()
                .param("RelayState",  _delegate + " https://unit.testfs.com")
                .post(LibParam.Identity.IDENTITY_RESP_PATH);
        assertEquals(400, response.statusCode());
    }

    @Test
    public void should400IfNoRelayStateResponseParam() throws Exception
    {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(Util.join(System.getProperty("user.dir"),
                "src/test/java/com/aerofs/sp/server/saml_resources/saml_test_assertion.txt")))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }

        String testAssertion = sb.toString();

        _session = _identitySessionManager.createSession(NONCE_LIFETIME_SECS);
        _delegate = _identitySessionManager.createDelegate(_session, NONCE_LIFETIME_SECS);

        Response response = given()
                .param("SAMLResponse", testAssertion)
                .post(LibParam.Identity.IDENTITY_RESP_PATH);
        assertEquals(400, response.statusCode());
    }

    @Test
    public void should400IfMalformedRelayStateResponseParam() throws Exception
    {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(Util.join(System.getProperty("user.dir"),
                "src/test/java/com/aerofs/sp/server/saml_resources/saml_test_assertion.txt")))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }

        String testAssertion = sb.toString();

        _session = _identitySessionManager.createSession(NONCE_LIFETIME_SECS);
        _delegate = _identitySessionManager.createDelegate(_session, NONCE_LIFETIME_SECS);

        Response response = given()
                .param("SAMLResponse", testAssertion)
                .param("RelayState", _delegate + " ")
                .post(LibParam.Identity.IDENTITY_RESP_PATH);
        assertEquals(400, response.statusCode());
    }

    @Test
    public void should400IfCannotResolveSAMLAssertion() throws Exception
    {
        String testAssertion = "becausethisisnotlegitassertion";

        _session = _identitySessionManager.createSession(NONCE_LIFETIME_SECS);
        _delegate = _identitySessionManager.createDelegate(_session, NONCE_LIFETIME_SECS);

        Response response = given()
                .param("SAMLResponse", testAssertion)
                .param("RelayState",  _delegate + " https://unit.testfs.com")
                .post(LibParam.Identity.IDENTITY_RESP_PATH);
        assertEquals(400, response.statusCode());
    }
}
