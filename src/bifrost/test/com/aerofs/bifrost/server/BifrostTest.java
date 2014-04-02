/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.UserID;
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
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.jayway.restassured.RestAssured;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;

import java.util.Map;
import java.util.Set;

import static com.jayway.restassured.config.RedirectConfig.redirectConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Base class to hold common OAuth functionality
 */
public abstract class BifrostTest extends AbstractTest
{
    public final static String RESOURCEKEY = "authorization-server-admin";
    public final static String RESOURCESECRET = "rs_secret";
    protected final static String CLIENTID = "test-app-unique-id";
    protected final static String CLIENTNAME = "test-app-name";
    protected final static String CLIENTSECRET = "test-app-secret";
    protected final static String CLIENTREDIRECT = "http://client.example.com:9000/redirect";
    protected final static String USERNAME = "user";
    public final static String RW_TOKEN = "rwtoken";
    public final static String RO_TOKEN = "rotoken";
    public final static String EXPIRED = "expired";
    protected final static String AUTH_URL = "/authorize";
    protected final static String TOKEN_URL = "/token";
    protected final static String USERS_URL = "/users";
    protected final static String TOKENLIST_URL = "/tokenlist";
    protected final static String CLIENTS_URL = "/clients";

    @Mock SessionFactory _sessionFactory;
    @Mock SPBlockingClient _spClient;
    @Mock SPBlockingClient.Factory _spClientFactory;
    @Mock Session _session;
    Bifrost _service;
    protected int _port;
    private Injector _injector;

    protected ClientRepository _clientRepository;
    protected AccessTokenRepository _accessTokenRepository;
    protected ResourceServerRepository _resourceServerRepository;
    protected static AuthorizationRequestDAO _authRequestDb;

    @Before
    public void setUp() throws Exception
    {
        _injector = Guice.createInjector(
                Bifrost.bifrostModule(),
                mockDatabaseModule(_sessionFactory),
                mockSPClientModule());

        _clientRepository = _injector.getInstance(ClientRepository.class);
        _accessTokenRepository = _injector.getInstance(AccessTokenRepository.class);
        _resourceServerRepository = _injector.getInstance(ResourceServerRepository.class);

        _service = new Bifrost(_injector, null);
        _service.start();
        _port = _service.getListeningPort();

        when(_sessionFactory.openSession()).thenReturn(_session);
        when(_sessionFactory.getCurrentSession()).thenReturn(_session);

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
        Map<String, String> res = Maps.newHashMap();

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
        ResourceServer rs = createResourceServer(inj);
        Client client = createClient(rs, inj);

        createAccessToken(client, inj, RW_TOKEN, user, OrganizationID.PRIVATE_ORGANIZATION, 0,
                ImmutableSet.of("files.read", "files.write"));
        createAccessToken(client, inj, RO_TOKEN, user, OrganizationID.PRIVATE_ORGANIZATION, 0,
                ImmutableSet.of("files.read"));
        createAccessToken(client, inj, EXPIRED, user, OrganizationID.PRIVATE_ORGANIZATION, 1,
                ImmutableSet.of("read", "write"));
    }

    public static ResourceServer createResourceServer(Injector inj)
    {
        ResourceServer rs = new ResourceServer();
        rs.updateTimeStamps();
        rs.setContactEmail("localadmin@example.com");
        rs.setContactName("local admin");
        rs.setName("Auth server");
        rs.setKey(RESOURCEKEY);
        rs.setSecret(RESOURCESECRET);
        rs.setScopes(ImmutableSet.of("files.read", "files.write"));
        inj.getInstance(ResourceServerRepository.class).save(rs);
        return rs;
    }

    public static Client createClient(ResourceServer rs, Injector inj)
    {
        Client client = new Client();
        client.setClientId(CLIENTID);
        client.setSecret(CLIENTSECRET);
        client.setName(CLIENTNAME);
        client.setRedirectUris(ImmutableList.of(CLIENTREDIRECT));
        client.updateTimeStamps();
        client.setContactEmail("test@example.com");
        client.setContactName("Test contact");
        client.setIncludePrincipal(true);
        client.setSkipConsent(false);
        client.setScopes(ImmutableSet.of("files.read", "files.write"));
        client.setResourceServer(rs);
        inj.getInstance(ClientRepository.class).save(client);

        Set<Client> clients = rs.getClients();
        if (clients != null){
            clients.addAll(rs.getClients());
        } else {
            rs.setClients(Sets.newHashSet(client));
        }
        inj.getInstance(ResourceServerRepository.class).save(rs);
        return client;
    }

    public static AccessToken createAccessToken(Client client, Injector inj, String tokenId,
            UserID user, OrganizationID org, long expires, Set<String> scopes)
    {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(USERNAME);
        principal.setEffectiveUserID(user);
        principal.setOrganizationID(org);
        AccessToken token = new AccessToken(tokenId,
                principal,
                client,
                expires,
                scopes,
                "");
        inj.getInstance(AccessTokenRepository.class).save(token);
        return token;
    }

    public static Module mockDatabaseModule(final SessionFactory sessionFactory)
    {
        return new AbstractModule()
        {
            @Override
            protected void configure()
            {
                _authRequestDb = new MockAuthRequestDAO(sessionFactory);

                bind(SessionFactory.class).toInstance(sessionFactory);

                bind(ClientDAO.class).toInstance(new MockClientDAO(sessionFactory));
                bind(AccessTokenDAO.class).to(MockAccessTokenDAO.class);
                bind(AuthorizationRequestDAO.class).toInstance(_authRequestDb);
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
                when(_spClientFactory.create()).thenReturn(_spClient);
            }
        };
    }
}
