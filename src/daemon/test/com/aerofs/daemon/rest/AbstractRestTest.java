package com.aerofs.daemon.rest;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.bifrost.server.Bifrost;
import com.aerofs.bifrost.server.BifrostTest;
import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.net.ClientSSLEngineFactory;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.util.RestObject;
import com.aerofs.havre.Havre;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SOID;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.testlib.TempCert;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.util.Timer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.Executors;

import static com.aerofs.base.TimerUtil.getGlobalTimer;
import static com.jayway.restassured.RestAssured.given;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base class for tests exercising the public REST API
 */
@RunWith(Parameterized.class)
public class AbstractRestTest extends AbstractTest
{
    @Parameters
    public static Collection<Object[]> data()
    {
        return Arrays.asList(new Object[][] {{false}, {true}});
    }

    protected static final Logger l = Loggers.getLogger(AbstractRestTest.class);

    protected @Mock DirectoryService ds;
    protected @Mock LocalACL acl;
    protected @Mock SIDMap sm;
    protected @Mock IStores ss;

    protected @Mock CfgLocalUser localUser;
    protected @Mock CfgLocalDID localDID;
    protected @Mock CfgKeyManagersProvider kmgr;
    protected @Mock CfgCACertificateProvider cacert;

    protected @Mock NativeVersionControl nvc;

    protected @InjectMocks ClientSSLEngineFactory clientSslEngineFactory;

    protected @Mock IPhysicalStorage ps;

    protected @Mock SessionFactory sessionFactory;
    protected @Mock Session session;

    private static TempCert ca;
    private static TempCert client;

    protected static final UserID user = UserID.fromInternal("foo@bar.baz");
    protected static final DID did = DID.generate();

    protected final boolean useProxy;

    public AbstractRestTest(boolean useProxy)
    {
        this.useProxy = useProxy;
    }

    @BeforeClass
    public static void generateCert()
    {
        ca = TempCert.generateCA();
        client = TempCert.generateDaemon(user, did, ca);
        RestAssured.keystore(ca.keyStore, TempCert.KS_PASSWD);
    }

    @AfterClass
    public static void cleanupCert()
    {
        ca.cleanup();
        client.cleanup();
    }

    protected MockDS mds;
    protected SID rootSID = SID.rootSID(user);

    private RestService service;

    private RestTunnelClient tunnel;
    private Havre havre;
    private Bifrost bifrost;

    protected final static DateFormat ISO_8601 = utcFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    protected final static byte[] VERSION_HASH =
            BaseSecUtil.newMessageDigestMD5().digest(new byte[0]);

    private static DateFormat utcFormat(String pattern) {
        DateFormat dateFormat = new SimpleDateFormat(pattern);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat;
    }

    @Before
    public void setUp() throws Exception
    {
        mds = new MockDS(rootSID, ds, sm, sm);
        mds.root();  // setup sid<->sidx mapping for root..

        when(localUser.get()).thenReturn(user);
        when(localDID.get()).thenReturn(did);

        when(kmgr.getCert()).thenReturn(client.cert);
        when(kmgr.getPrivateKey()).thenReturn(client.key);

        when(cacert.getCert()).thenReturn(ca.cert);

        when(sessionFactory.openSession()).thenReturn(session);

        when(nvc.getVersionHash_(any(SOID.class))).thenReturn(VERSION_HASH);

        Properties prop = new Properties();
        prop.setProperty("bifrost.port", "0");
        ConfigurationProperties.setProperties(prop);

        // start OAuth service
        bifrost = new Bifrost(bifrostInjector(), kmgr);
        bifrost.start();
        l.info("OAuth service at {}", bifrost.getListeningPort());

        String bifrostUrl =
                "https://localhost:" + bifrost.getListeningPort() + "/tokeninfo";

        prop.setProperty("api.daemon.port", "0");
        prop.setProperty("api.tunnel.host", "localhost");
        prop.setProperty("api.tunnel.port", "48808");
        prop.setProperty("daemon.oauth.id", BifrostTest.RESOURCEKEY);
        prop.setProperty("daemon.oauth.secret", BifrostTest.RESOURCESECRET);
        prop.setProperty("daemon.oauth.url", bifrostUrl);
        prop.setProperty("havre.tunnel.host", "localhost");
        prop.setProperty("havre.tunnel.port", "48808");
        prop.setProperty("havre.proxy.host", "localhost");
        prop.setProperty("havre.proxy.port", "0");
        prop.setProperty("havre.oauth.id", BifrostTest.RESOURCEKEY);
        prop.setProperty("havre.oauth.secret", BifrostTest.RESOURCESECRET);
        prop.setProperty("havre.oauth.url", bifrostUrl);
        ConfigurationProperties.setProperties(prop);

        // start REST service
        Injector inj = coreInjector();
        service = new RestService(inj, kmgr) {
            @Override
            protected ServerSocketChannelFactory getServerSocketFactory()
            {
                return new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool());
            }
        };
        service.start();
        RestAssured.baseURI = "https://localhost";
        RestAssured.port = service.getListeningPort();
        l.info("REST service at {}", RestAssured.port);

        if (useProxy) {
            // start local gateway
            havre = new Havre(user, did, kmgr, kmgr, cacert);
            havre.start();
            RestAssured.port = havre.getProxyPort();

            l.info("REST gateway at {}", RestAssured.port);

            // open tunnel between gateway and rest service
            tunnel = new RestTunnelClient(localUser, localDID, getGlobalTimer(),
                    clientSslEngineFactory, service);
            tunnel.start().awaitUninterruptibly();
        }
    }

    private Injector coreInjector()
    {
        final IIMCExecutor imce = mock(IIMCExecutor.class);

        Injector inj = Guice.createInjector(new RestModule(), new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind(CfgLocalUser.class).toInstance(localUser);
                bind(CfgCACertificateProvider.class).toInstance(cacert);
                bind(IStores.class).toInstance(ss);
                bind(NativeVersionControl.class).toInstance(nvc);
                bind(DirectoryService.class).toInstance(ds);
                bind(LocalACL.class).toInstance(acl);
                bind(IMapSID2SIndex.class).toInstance(sm);
                bind(IMapSIndex2SID.class).toInstance(sm);
                bind(IPhysicalStorage.class).toInstance(ps);
                bind(Timer.class).toInstance(getGlobalTimer());
                bind(CoreIMCExecutor.class).toInstance(new CoreIMCExecutor(imce));
            }
        });

        // wire event handlers (no queue, events are immediately executed)
        ICoreEventHandlerRegistrar reg = inj.getInstance(RestCoreEventHandlerRegistar.class);
        final CoreEventDispatcher disp = new CoreEventDispatcher(Collections.singleton(reg));
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                Object[] args = invocation.getArguments();
                disp.dispatch_((IEvent)args[0], (Prio)args[1]);
                return null;
            }
        }).when(imce).execute_(any(IEvent.class), any(Prio.class));

        return inj;
    }

    private Injector bifrostInjector() throws Exception
    {
        final SPBlockingClient.Factory factSP = mock(SPBlockingClient.Factory.class);
        SPBlockingClient sp = mock(SPBlockingClient.class);
        when(factSP.create()).thenReturn(sp);
        when(sp.signInRemote()).thenReturn(sp);

        Injector inj = Guice.createInjector(Bifrost.bifrostModule(),
                BifrostTest.mockDatabaseModule(sessionFactory),
                new AbstractModule() {
                    @Override
                    protected void configure()
                    {
                        bind(SPBlockingClient.Factory.class).toInstance(factSP);
                    }
                });

        BifrostTest.createTestEntities(user, inj);

        return inj;
    }

    @After
    public void tearDown() throws Exception
    {
        service.stop();
        if (useProxy) {
            havre.stop();
            tunnel.stop();
        }
        bifrost.stop();
    }

    protected RestObject object(String path) throws SQLException
    {
        SOID soid = ds.resolveNullable_(Path.fromString(rootSID, path));
        assertNotNull(path, soid);
        SID sid = sm.get_(soid.sidx());
        return new RestObject(sid, soid.oid());
    }

    protected RequestSpecification givenAcces()
    {
        return given()
                .header(Names.AUTHORIZATION, "Bearer " + BifrostTest.TOKEN);
    }
}
