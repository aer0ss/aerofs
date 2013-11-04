/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.base.net.IURLConnectionConfigurator;
import com.aerofs.bifrost.module.AccessTokenDAO;
import com.aerofs.bifrost.module.AuthorizationRequestDAO;
import com.aerofs.bifrost.module.ClientDAO;
import com.aerofs.bifrost.module.ResourceServerDAO;
import com.aerofs.bifrost.oaaas.model.AccessToken;
import com.aerofs.bifrost.oaaas.model.Client;
import com.aerofs.bifrost.oaaas.model.ResourceServer;
import com.aerofs.bifrost.oaaas.repository.AccessTokenRepository;
import com.aerofs.bifrost.oaaas.repository.ClientRepository;
import com.aerofs.bifrost.oaaas.repository.ResourceServerRepository;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.testlib.TempCert;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
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
import org.mockito.Matchers;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.google.inject.matcher.Matchers.any;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.RedirectConfig.redirectConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static com.jayway.restassured.path.json.JsonPath.from;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base class to hold common OAuth functionality
 */
public abstract class BifrostTest extends AbstractTest
{
    public final static String RESOURCEID = "authorization-server-admin";
    protected final static String CLIENTSECRET = "secret";
    public final static String RESOURCESECRET = "rs_secret";
    protected final static String CLIENTREDIRECT = "http://client.example.com:9000/redirect";
    protected final static String USERNAME = "user";
    public final static String TOKEN = "token";
    protected final static String AUTH_URL = "/authorize";
    protected final static String TOKEN_URL = "/token";
    protected final static String CLIENTID = "testapp";

    @Mock SessionFactory _sessionFactory;
    @Mock SPBlockingClient _spClient;
    @Mock SPBlockingClient.Factory _spClientFactory;
    @Mock Session _session;
    Bifrost _service;
    protected int _port;
    private Injector _injector;

    @Before
    public void setUp() throws Exception
    {
        _injector = Guice.createInjector(
                Bifrost.bifrostModule(),
                mockDatabaseModule(_sessionFactory),
                mockSPClientModule());

        _service = new Bifrost(_injector, null);
        _service.start();
        _port = _service.getListeningPort();

        when(_sessionFactory.openSession()).thenReturn(_session);

        RestAssured.baseURI = "http://localhost";
        RestAssured.port = _port;
        RestAssured.config = newConfig().redirect(redirectConfig().followRedirects(false));

        createTestEntities(UserID.fromInternal(USERNAME), _injector);
        l.info("Bifrost service started at {}", RestAssured.port);
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
    public static void createTestEntities(UserID user, Injector inj)
    {
        Client client = new Client();
        ResourceServer rs = new ResourceServer();
        Set<Client> clients = Sets.newHashSet();
        Set<String> scopes = Sets.newHashSet();

        rs.updateTimeStamps();
        rs.setContactEmail("localadmin@example.com");
        rs.setContactName("local admin");
        rs.setName("Auth server");
        rs.setKey(RESOURCEID);
        rs.setSecret(RESOURCESECRET);

        client.setClientId(CLIENTID);
        client.setSecret(CLIENTSECRET);
        client.setRedirectUris(ImmutableList.of(CLIENTREDIRECT));
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

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(USERNAME);
        principal.setUserID(user);
        principal.setOrganizationID(OrganizationID.PRIVATE_ORGANIZATION);
        AccessToken token = new AccessToken(TOKEN,
                principal,
                client,
                0,
                scopes,
                "");

        inj.getInstance(ResourceServerRepository.class).save(rs);
        inj.getInstance(ClientRepository.class).save(client);
        inj.getInstance(AccessTokenRepository.class).save(token);
    }

    public static Module mockDatabaseModule(final SessionFactory sessionFactory)
    {
        return new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind(SessionFactory.class).toInstance(sessionFactory);

                bind(ClientDAO.class).toInstance(new MockClientDAO(sessionFactory));
                bind(AccessTokenDAO.class).to(MockAccessTokenDAO.class);
                bind(AuthorizationRequestDAO.class).to(MockAuthRequestDAO.class);
                bind(ResourceServerDAO.class).to(MockResourceServerDAO.class);
            }
        };
    }

    private Module mockSPClientModule()
    {
        return new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind(SPBlockingClient.class).toInstance(_spClient);
                bind(SPBlockingClient.Factory.class).toInstance(_spClientFactory);
                when(_spClientFactory.create_(Matchers.<IURLConnectionConfigurator>anyObject()))
                        .thenReturn(_spClient);
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
