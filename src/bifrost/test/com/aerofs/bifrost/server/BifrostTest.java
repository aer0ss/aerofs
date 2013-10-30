/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.bifrost.module.AccessTokenDAO;
import com.aerofs.bifrost.module.AuthorizationRequestDAO;
import com.aerofs.bifrost.module.ClientDAO;
import com.aerofs.bifrost.module.ResourceServerDAO;
import com.aerofs.bifrost.oaaas.model.Client;
import com.aerofs.bifrost.oaaas.model.ResourceServer;
import com.aerofs.bifrost.oaaas.repository.ClientRepository;
import com.aerofs.bifrost.oaaas.repository.ResourceServerRepository;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.testlib.TempCert;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.RedirectConfig.redirectConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static com.jayway.restassured.path.json.JsonPath.from;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Base class to hold common OAuth functionality
 */
public abstract class BifrostTest extends AbstractTest
{
    final static String RESOURCEID = "authorization-server-admin";
    final static String CLIENTSECRET = "secret";
    final static String RESOURCESECRET = "rs_secret";
    final static String CLIENTREDIRECT = "http://client.example.com:9000/redirect";
    private static TempCert ca;
    private static TempCert cert;
    protected final String AUTH_URL = "/authorize";
    protected final String CLIENTID = "testapp";
    @Mock CfgKeyManagersProvider _kmgr;
    @Mock SessionFactory _sessionFactory;
    @Mock Session _session;
    Bifrost _service;
    private int _port;
    private Injector _injector;

    @BeforeClass
    public static void generateCert()
    {
        ca = TempCert.generateCA();
        cert = TempCert.generate("baroo", ca);
        RestAssured.keystore(ca.keyStore, TempCert.KS_PASSWD);
    }

    @AfterClass
    public static void cleanupCert()
    {
        ca.cleanup();
        cert.cleanup();
    }

    @Before
    public void setUp()
    {
        _injector = Guice.createInjector(Bifrost.bifrostModule(), mockDatabaseModule());

        _service = new Bifrost(_injector, _kmgr);
        _service.start();
        _port = _service.getListeningPort();

        when(_sessionFactory.openSession()).thenReturn(_session);
        when(_kmgr.getCert()).thenReturn(cert.cert);
        when(_kmgr.getPrivateKey()).thenReturn(cert.key);

        RestAssured.baseURI = "https://localhost";
        RestAssured.port = _port;
        RestAssured.config = newConfig().redirect(redirectConfig().followRedirects(false));

        createTestEntities();

        l.info("REST service started at {}", RestAssured.port);
    }

    @After
    public void tearDown()
    {
        _service.stop();
    }

    protected static Map<String, String> extractQuery(String location)
    {
        HashMap<String, String> res = new HashMap<String, String>();

        assertTrue(location.contains("?"));
        String query = location.substring(location.lastIndexOf("?") + 1);

        for (String pair : query.split("&")) {
            String[] elems = pair.split("=");
            res.put(elems[0], elems[1]);
        }
        return res;
    }

    /**
     * Create a client and resource server for testing.
     * client: use CLIENTID, CLIENTSECRET
     * resourceserver: use
     */
    private void createTestEntities()
    {
        Client client = new Client();
        ResourceServer rs = new ResourceServer();
        Set<Client> clients = new HashSet<Client>();
        Set<String> scopes = new HashSet<String>();

        rs.updateTimeStamps();
        rs.setContactEmail("localadmin@example.com");
        rs.setContactName("local admin");
        rs.setName("Auth server");
        rs.setKey(RESOURCEID);
        rs.setSecret(RESOURCESECRET);

        client.setClientId(CLIENTID);
        client.setSecret(CLIENTSECRET);
        client.setRedirectUris(new ArrayList<String>(Arrays.asList(new String[]{CLIENTREDIRECT})));
        client.updateTimeStamps();
        client.setContactEmail("test@example.com");
        client.setName("Test contact");
        client.setIncludePrincipal(true);
        client.setSkipConsent(false);

        scopes.add("read");
        scopes.add("write");
        client.setScopes(scopes);
        rs.setScopes(scopes);

        client.setResourceServer(rs);
        clients.add(client);

        rs.setClients(clients);

        _injector.getInstance(ResourceServerRepository.class).save(rs);
        _injector.getInstance(ClientRepository.class).save(client);
    }

    private Module mockDatabaseModule()
    {
        return new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind(SessionFactory.class).toInstance(_sessionFactory);

                bind(ClientDAO.class).toInstance(new MockClientDAO(_sessionFactory));
                bind(AccessTokenDAO.class).to(MockAccessTokenDAO.class);
                bind(AuthorizationRequestDAO.class).to(MockAuthRequestDAO.class);
                bind(ResourceServerDAO.class).to(MockResourceServerDAO.class);
            }
        };
    }

    protected RequestSpecification oauthReq()
    {
        return given()
                .param("response_type", "code")
                .param("client_id", CLIENTID)
                .param("redirect_uri", CLIENTREDIRECT)
                .param("state", "client_state");
    }

    /** Return an AUTH_STATE to use for authenticating */
    protected String oauthBegin()
    {
        String resp = oauthReq().get(AUTH_URL).asString();
        return from(resp).get("auth_state");
    }
}
