package com.aerofs.daemon.rest;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.MDID;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.RestObject;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.bifrost.server.Bifrost;
import com.aerofs.bifrost.server.BifrostTest;
import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.activity.OutboundEventLogger;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.migration.ImmigrantCreator;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.mock.logical.MockDS.MockDSDir;
import com.aerofs.daemon.core.net.ClientSSLEngineFactory;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.rest.util.EntityTagUtil;
import com.aerofs.havre.Havre;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgStorageType;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.oauth.TokenVerificationClient;
import com.aerofs.oauth.TokenVerifier;
import com.aerofs.oauth.VerifyTokenResponse;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.testlib.TempCert;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.ObjectMapperConfig;
import com.jayway.restassured.config.RestAssuredConfig;
import com.jayway.restassured.mapper.factory.GsonObjectMapperFactory;
import com.jayway.restassured.specification.RequestSpecification;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
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
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;

import javax.ws.rs.core.EntityTag;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Permission;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static com.aerofs.base.TimerUtil.getGlobalTimer;
import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
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
    protected @Mock IPhysicalStorage ps;
    protected @Mock IOSUtil os;
    protected @Mock CfgStorageType storageType;
    protected @Mock CfgAbsRoots absRoots;

    protected @Mock LocalACL acl;
    protected @Mock SIDMap sm;
    protected @Mock IStores ss;

    protected @Mock CfgLocalUser localUser;
    protected @Mock CfgLocalDID localDID;
    protected static CfgKeyManagersProvider kmgr;
    protected static CfgCACertificateProvider cacert;

    protected @Mock NativeVersionControl nvc;

    protected @InjectMocks ClientSSLEngineFactory clientSslEngineFactory;

    protected @Mock Trans t;
    protected @Mock TransManager tm;

    protected @Mock Token tk;
    protected @Mock TokenManager tokenManager;
    protected @Mock TCB tcb;
    protected @Spy TokenVerifier tokenVerifier = new TokenVerifier(
            BifrostTest.CLIENTID,
            BifrostTest.CLIENTSECRET,
            mock(TokenVerificationClient.class),
            CacheBuilder.newBuilder());

    protected @Mock ObjectCreator oc;
    protected @Mock ObjectDeleter od;
    protected @Mock VersionUpdater vu;
    protected @Mock Expulsion expulsion;
    protected ObjectMover om;
    protected @Mock ImmigrantCreator ic;

    protected @Spy InMemoryPrefix pf = new InMemoryPrefix();


    protected class InMemoryPrefix implements IPhysicalPrefix
    {
        private ByteArrayOutputStream baos = new ByteArrayOutputStream();

        public byte[] data()
        {
            return baos.toByteArray();
        }

        @Override
        public long getLength_()
        {
            return baos.toByteArray().length;
        }

        @Override
        public OutputStream newOutputStream_(boolean append) throws IOException
        {
            if (!append) baos = new ByteArrayOutputStream();
            return baos;
        }

        @Override
        public InputStream newInputStream_() throws IOException
        {
            if (getLength_() == 0) throw new FileNotFoundException();
            return new ByteArrayInputStream(data());
        }

        @Override
        public void moveTo_(IPhysicalPrefix pf, Trans t) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void prepare_(Token tk) throws IOException {}

        @Override
        public void delete_() throws IOException
        {
            baos = new ByteArrayOutputStream();
        }

        @Override
        public void truncate_(long length) throws IOException
        {
            baos = new ByteArrayOutputStream();
            baos.write(Arrays.copyOf(data(), (int)length));
        }
    }

    protected  @Mock OutboundEventLogger oel;

    protected static SessionFactory sessionFactory;
    protected static Session session;

    private static TempCert ca;
    private static TempCert client;

    protected static final UserID user = UserID.fromInternal("foo@bar.baz");
    protected static final DID did = DID.generate();

    protected final boolean useProxy;

    public AbstractRestTest(boolean useProxy)
    {
        this.useProxy = useProxy;
    }

    /** Temporary measure, trying to debug a weird "unexpected exit" that is occurring in CI. */
    private static class TestSecurityManager extends SecurityManager {
        @Override
        public void checkExit( final int status ) {
            SecurityException sec = new SecurityException( "System.exit(" + status + ")!!");
            sec.printStackTrace();
            throw sec;
        }
        @Override
        public void checkPermission( final Permission perm ) { }
        @Override
        public void checkPermission( final Permission perm, final Object context ) { }
    }

    @BeforeClass
    public static void commonSetup() throws Exception
    {
        System.setSecurityManager(new TestSecurityManager());

        ca = TempCert.generateCA();
        client = TempCert.generateDaemon(user, did, ca);
        RestAssured.keystore(ca.keyStore, TempCert.KS_PASSWD);

        cacert = mock(CfgCACertificateProvider.class);
        when(cacert.getCert()).thenReturn(ca.cert);

        kmgr = mock(CfgKeyManagersProvider.class);
        when(kmgr.getCert()).thenReturn(client.cert);
        when(kmgr.getPrivateKey()).thenReturn(client.key);

        sessionFactory = mock(SessionFactory.class);
        session = mock(Session.class);

        when(sessionFactory.openSession()).thenReturn(session);

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

        RestAssured.config = RestAssuredConfig.config()
                .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
                        .gsonObjectMapperFactory(new GOMF()));
    }

    private static class GOMF implements GsonObjectMapperFactory
    {
        @Override
        public Gson create(Class cls, String charset)
        {
            return new GsonBuilder()
                    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        }
    }

    @AfterClass
    public static void commonCleanup()
    {
        bifrost.stop();

        ca.cleanup();
        client.cleanup();
        /* temporary measure debugging CI */
        System.setSecurityManager(null);
    }

    protected MockDS mds;
    protected SID rootSID = SID.rootSID(user);

    private RestService service;

    private Injector inj;

    private RestTunnelClient tunnel;
    private static Havre havre;
    private static Bifrost bifrost;

    protected final static DateFormat ISO_8601 = utcFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    protected final static byte[] VERSION_HASH =
            BaseSecUtil.newMessageDigestMD5().digest(new byte[]{0});

    protected final String CURRENT_ETAG_VALUE = BaseUtil.hexEncode(VERSION_HASH);
    protected final String CURRENT_ETAG = "\"" + CURRENT_ETAG_VALUE + "\"";

    protected final static byte[] OTHER_HASH =
            BaseSecUtil.newMessageDigestMD5().digest(new byte[] {1});

    protected final String OTHER_ETAG = "\"" + BaseUtil.hexEncode(OTHER_HASH) + "\"";

    private static Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

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

        om = spy(new ObjectMover(vu, ds, expulsion));

        when(ps.newPrefix_(any(SOCKID.class), anyString())).thenReturn(pf);

        SIndex rootSidx = sm.get_(rootSID);
        when(ss.getParents_(argThat(not(equalTo(rootSidx))))).thenReturn(ImmutableSet.of(rootSidx));

        when(localUser.get()).thenReturn(user);
        when(localDID.get()).thenReturn(did);

        when(tm.begin_()).thenReturn(t);
        when(tokenManager.acquireThrows_(any(Cat.class), anyString())).thenReturn(tk);
        when(tokenManager.acquire_(any(Cat.class), anyString())).thenReturn(tk);
        when(tk.pseudoPause_(anyString())).thenReturn(tcb);

        when(nvc.getVersionHash_(any(SOID.class), any(CID.class))).thenReturn(VERSION_HASH);

        when(ic.move_(any(SOID.class), any(SOID.class), anyString(), any(PhysicalOp.class), eq(t)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable
                    {
                        SOID soid = (SOID)invocation.getArguments()[0];
                        SOID soidToParent = (SOID)invocation.getArguments()[1];
                        String toName = (String)invocation.getArguments()[2];
                        PhysicalOp op = (PhysicalOp)invocation.getArguments()[3];
                        if (soidToParent.sidx().equals(soid.sidx())) {
                            om.moveInSameStore_(soid, soidToParent.oid(), toName, op, false, true,
                                    t);
                            return soid;
                        } else {
                            return ic.createImmigrantRecursively_(ds.resolve_(soid).parent(), soid,
                                    soidToParent, toName, op, t);
                        }
                    }
                });

        // start local gateway
        havre = new Havre(user, did, kmgr, kmgr, cacert, getGlobalTimer(), tokenVerifier);
        havre.start();

        // start REST service
        inj = coreInjector();
        service = new RestService(inj, kmgr) {
            @Override
            protected ServerSocketChannelFactory getServerSocketFactory()
            {
                return new NioServerSocketChannelFactory(
                        Executors.newSingleThreadExecutor(),
                        Executors.newFixedThreadPool(2));
            }
        };
        service.start();
        RestAssured.baseURI = "https://localhost";
        RestAssured.port = service.getListeningPort();
        l.info("REST service at {}", RestAssured.port);

        if (useProxy) {
            RestAssured.port = havre.getProxyPort();

            l.info("REST gateway at {}", RestAssured.port);

            // open tunnel between gateway and rest service
            tunnel = new RestTunnelClient(localUser, localDID, getGlobalTimer(),
                    clientSslEngineFactory, service);
            tunnel.start().awaitUninterruptibly();
        }
    }

    protected EntityTag etagForMeta(SOID soid) throws SQLException
    {
        return inj.getInstance(EntityTagUtil.class).etagForMeta(soid);
    }


    private Injector coreInjector()
    {
        final IIMCExecutor imce = mock(IIMCExecutor.class);

        Injector inj = Guice.createInjector(new RestModule(tokenVerifier), new AbstractModule()
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
                bind(TransManager.class).toInstance(tm);
                bind(TokenManager.class).toInstance(tokenManager);
                bind(ObjectCreator.class).toInstance(oc);
                bind(ObjectMover.class).toInstance(om);
                bind(ObjectDeleter.class).toInstance(od);
                bind(OutboundEventLogger.class).toInstance(oel);
                bind(ImmigrantCreator.class).toInstance(ic);
                bind(VersionUpdater.class).toInstance(vu);
                bind(IIMCExecutor.class).toInstance(imce);
                bind(IOSUtil.class).toInstance(os);
                bind(CfgStorageType.class).toInstance(storageType);
                bind(CfgAbsRoots.class).toInstance(absRoots);
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

    private static Injector bifrostInjector() throws Exception
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
        havre.stop();
        service.stop();
        if (useProxy) {
            tunnel.stop();
        }
    }

    protected RestObject object(String path) throws SQLException
    {
        SOID soid = ds.resolveNullable_(Path.fromString(rootSID, path));
        assertNotNull(path, soid);
        SID sid = sm.get_(soid.sidx());
        return new RestObject(sid, soid.oid());
    }

    protected String id(OID oid)
    {
        return new RestObject(rootSID, oid).toStringFormal();
    }

    protected String id(SOID soid)
    {
        return id(soid.oid());
    }

    protected RequestSpecification givenTokenWithScopes(Set<String> scopes)
            throws Exception
    {
        String token = UniqueID.generate().toStringFormal();
        VerifyTokenResponse response = new VerifyTokenResponse(
                BifrostTest.CLIENTID,
                scopes,
                0L,
                new AuthenticatedPrincipal(user.getString(), user, OrganizationID.PRIVATE_ORGANIZATION),
                MDID.generate().toStringFormal());
        doReturn(response).when(tokenVerifier).verifyToken(anyString());
        return given()
                .header(Names.AUTHORIZATION, "Bearer " + token);
    }

    protected RequestSpecification givenAccess() throws Exception
    {
        return givenTokenWithScopes(ImmutableSet.of("files.read", "files.write"));
    }

    protected RequestSpecification givenReadAccess() throws Exception
    {
        return givenTokenWithScopes(ImmutableSet.of("files.read"));
    }

    protected RequestSpecification givenReadAccessTo(RestObject object) throws Exception
    {
        return givenTokenWithScopes(ImmutableSet.of("files.read:" + object.toStringFormal()));
    }

    protected RequestSpecification givenExpiredToken() throws Exception
    {
        doReturn(VerifyTokenResponse.EXPIRED).when(tokenVerifier).verifyToken(anyString());
        return given()
                .header(Names.AUTHORIZATION, "Bearer " + UniqueID.generate().toStringFormal());
    }

    protected RequestSpecification givenInvalidToken() throws Exception
    {
        doReturn(VerifyTokenResponse.NOT_FOUND).when(tokenVerifier).verifyToken(anyString());
        return given()
                .header(Names.AUTHORIZATION, "Bearer invalid");
    }

    protected static Matcher<String> matches(final String regex)
    {
        return new BaseMatcher<String>() {
            final Pattern p = Pattern.compile(regex);
            @Override
            public boolean matches(Object o)
            {
                return o instanceof String && (p.matcher((String)o).matches());
            }

            @Override
            public void describeTo(Description description)
            {
                description.appendText("matches(" + regex + ")");
            }
        };
    }

    protected static Matcher<String> anyUUID()
    {
        return matches("[0-9a-fA-F]{32}");
    }

    protected static String json(Object o) { return _gson.toJson(o); }

    private class EqualFutureObject extends BaseMatcher<String>
    {
        private final SettableFuture<SOID> future;

        public EqualFutureObject(SettableFuture<SOID> future)
        {
            this.future = future;
        }

        @Override
        public boolean matches(Object o)
        {
            try {
                SOID soid = future.get();
                return equalTo(new RestObject(sm.get_(soid.sidx()), soid.oid()).toStringFormal())
                        .matches(o);
            } catch (Exception e) {
                fail();
                return false;
            }
        }

        @Override
        public void describeTo(Description description)
        {
            try {
                description.appendValue(new RestObject(rootSID, future.get().oid()).toStringFormal());
            } catch (Exception e) { }
        }
    }

    protected Matcher<String> equalToFutureObject(SettableFuture<SOID> soid)
    {
        return new EqualFutureObject(soid);
    }

    protected SettableFuture<SOID> whenCreate(Type type, String parent, String name)
            throws Exception
    {
        SOID p = ds.resolveFollowAnchorThrows_(Path.fromString(rootSID, parent));
        final SettableFuture<SOID> soid = SettableFuture.create();

        when(oc.create_(eq(type), eq(p), eq(name), eq(PhysicalOp.APPLY), eq(t)))
                .thenAnswer(new Answer<Object>()
                {
                    @Override
                    public Object answer(InvocationOnMock invocation)
                            throws Throwable
                    {
                        Object[] args = invocation.getArguments();
                        MockDSDir d = mds.root().dir((String)args[2]);
                        soid.set(d.soid());
                        return d.soid();
                    }
                })
                .thenThrow(new ExAlreadyExist());

        return soid;
    }

    protected SettableFuture<SOID> whenMove(String objectName, String parentName, String newName)
            throws Exception
    {
        final SOID parentSoid = ds.resolveFollowAnchorThrows_(Path.fromString(rootSID, parentName));
        final SOID objectSoid = ds.resolveThrows_(Path.fromString(rootSID, objectName));
        final SettableFuture<SOID> soid = SettableFuture.create();
        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable
            {
                Object[] args = invocation.getArguments();
                OA oa = (OA)args[0];
                OA parent = (OA)args[1];
                Path pathTo = ds.resolve_(parent).append((String)args[2]);
                if (ds.resolveNullable_(pathTo) == null) {
                    mds.move(ds.resolve_(oa).toStringRelative(), pathTo.toStringRelative(), t);
                    soid.set(oa.soid());
                } else {
                    throw new ExAlreadyExist();
                }
                return null;
            }
        }).when(ds).setOAParentAndName_(any(OA.class), any(OA.class), anyString(), eq(t));
        when(ic.createImmigrantRecursively_(any(ResolvedPath.class), eq(objectSoid), eq(parentSoid),
                eq(newName), eq(PhysicalOp.APPLY), eq(t)))
                .thenAnswer(new Answer<Object>()
                {
                    @Override
                    public Object answer(InvocationOnMock invocation)
                            throws Throwable
                    {
                        Object[] args = invocation.getArguments();
                        SOID objectSoid = (SOID)args[1];
                        SOID parentSoid = (SOID)args[2];
                        String pathFrom = ds.resolve_(objectSoid).toStringRelative();
                        String pathTo = ds.resolve_(parentSoid).append((String)args[3]).toStringRelative();
                        if (mds.resolve(new Path(rootSID, pathTo.split("/"))) == null) {
                            // does not exist yet
                            mds.move(pathFrom, pathTo, t);
                            SOID r = new SOID(parentSoid.sidx(), objectSoid.oid());
                            soid.set(r);
                            return r;
                        } else { // already exists
                            throw new ExAlreadyExist();
                        }
                    }
                });

        return soid;
    }
}
