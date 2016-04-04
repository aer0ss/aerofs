package com.aerofs.daemon.rest;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.bifrost.server.Bifrost;
import com.aerofs.bifrost.server.BifrostTest;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.net.ClientSSLEngineFactory;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PrefixOutputStream;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.submit.ContentAvailabilitySubmitter;
import com.aerofs.daemon.core.polaris.submit.ContentChangeSubmitter;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.ICollectorStateDatabase;
import com.aerofs.daemon.lib.db.IDID2UserDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.havre.Havre;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.cfg.*;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.oauth.TokenVerificationClient;
import com.aerofs.oauth.TokenVerifier;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.testlib.TempCert;
import com.google.common.cache.CacheBuilder;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.*;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.ObjectMapperConfig;
import com.jayway.restassured.config.RestAssuredConfig;
import com.jayway.restassured.config.SSLConfig;
import com.jayway.restassured.mapper.factory.GsonObjectMapperFactory;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.Permission;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.aerofs.base.TimerUtil.getGlobalTimer;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BaseAbstractRestTest extends AbstractTest
{
    protected @Spy InMemoryPrefix pf = new InMemoryPrefix();

    protected @InjectMocks ClientSSLEngineFactory clientSslEngineFactory;
    protected @Mock IPhysicalStorage ps;
    protected @Mock IOSUtil os;
    protected @Mock CfgStorageType storageType;
    protected @Mock CfgAbsRoots absRoots;

    protected @Mock LocalACL acl;
    protected @Mock SIDMap sm;
    protected @Mock StoreHierarchy ss;
    protected @Mock IDID2UserDatabase did2user;

    protected @Mock Trans t;
    protected @Mock TransManager tm;
    protected @Mock Token tk;
    protected @Mock TokenManager tokenManager;
    protected @Mock TC.TCB tcb;
    protected @Mock CfgLocalUser localUser;
    protected @Mock CfgLocalDID localDID;

    protected @Mock ContentChangeSubmitter ccsub;
    protected @Mock ContentAvailabilitySubmitter casub;

    protected @Spy TokenVerifier tokenVerifier = new TokenVerifier(
            mock(TokenVerificationClient.class),
            CacheBuilder.newBuilder());

    protected @Mock ICollectorStateDatabase csdb;
    protected @Mock CentralVersionDatabase _cvdb;

    protected static CfgKeyManagersProvider kmgr;
    protected static CfgCACertificateProvider cacert;
    private static Bifrost bifrost;

    private static TempCert ca;
    private static TempCert client;
    private static TempCert gateway;

    protected static final UserID user = UserID.fromInternal("foo@bar.baz");
    protected SID rootSID = SID.rootSID(user);
    protected static final DID did = DID.generate();
    protected static SessionFactory sessionFactory;
    protected static Session session;
    private static Properties prop;

    protected RestService service;
    protected Injector inj;
    protected RestTunnelClient tunnel;
    protected Havre havre;

    protected final boolean useProxy;
    protected final static DateFormat ISO_8601 = utcFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private static Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    protected static byte[] FILE_CONTENT = {'H', 'e', 'l', 'l', 'o'};
    protected final static byte[] CONTENT_HASH =
            BaseSecUtil.newMessageDigest().digest(FILE_CONTENT);

    protected String CURRENT_ETAG_VALUE = BaseUtil.hexEncode(CONTENT_HASH);
    protected String CURRENT_ETAG = "\"" + CURRENT_ETAG_VALUE + "\"";

    private final static byte[] OTHER_HASH =
            BaseSecUtil.newMessageDigest().digest(new byte[]{1});
    protected final String OTHER_ETAG = "\"" + BaseUtil.hexEncode(OTHER_HASH) + "\"";

    public BaseAbstractRestTest(boolean useProxy)
    {
        this.useProxy = useProxy;
    }

    private static DateFormat utcFormat(String pattern) {
        DateFormat dateFormat = new SimpleDateFormat(pattern);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat;
    }

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
        public byte[] hashState_()
        {
            return null;
        }

        @Override
        public PrefixOutputStream newOutputStream_(boolean append) throws IOException
        {
            if (!append) baos = new ByteArrayOutputStream();
            return new PrefixOutputStream(baos);
        }

        @Override
        public void moveTo_(IPhysicalPrefix pf, Trans t) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete_() throws IOException
        {
            baos = new ByteArrayOutputStream();
        }
    }

    protected class TestTokenVerifierModule extends AbstractModule
    {
        @Override
        protected void configure() {
        }

        @Provides
        @Singleton
        public TokenVerifier providesVerifier()
        {
            return tokenVerifier;
        }
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
        gateway = TempCert.generateDaemon(UserID.DUMMY, new DID(UniqueID.ZERO), ca);

        cacert = mock(CfgCACertificateProvider.class);
        when(cacert.getCert()).thenReturn(ca.cert);

        kmgr = mock(CfgKeyManagersProvider.class);
        when(kmgr.getCert()).thenReturn(client.cert);
        when(kmgr.getPrivateKey()).thenReturn(client.key);

        sessionFactory = mock(SessionFactory.class);
        session = mock(Session.class);

        when(sessionFactory.openSession()).thenReturn(session);

        prop = new Properties();
        prop.setProperty("bifrost.port", "0");
        ConfigurationProperties.setProperties(prop);

        // start OAuth service
        bifrost = new Bifrost(bifrostInjector(), testDeploymentSecret());
        bifrost.start();
        l.info("OAuth service at {}", bifrost.getListeningPort());

        String bifrostUrl =
                "http://localhost:" + bifrost.getListeningPort() + "/tokeninfo";

        prop.setProperty("api.daemon.port", "0");
        prop.setProperty("api.tunnel.host", "localhost");
        prop.setProperty("daemon.oauth.id", BifrostTest.RESOURCEKEY);
        prop.setProperty("daemon.oauth.secret", BifrostTest.RESOURCESECRET);
        prop.setProperty("daemon.oauth.url", bifrostUrl);
        prop.setProperty("havre.tunnel.host", "localhost");
        prop.setProperty("havre.tunnel.port", "0");
        prop.setProperty("havre.proxy.host", "localhost");
        prop.setProperty("havre.proxy.port", "0");
        prop.setProperty("havre.oauth.id", BifrostTest.RESOURCEKEY);
        prop.setProperty("havre.oauth.secret", BifrostTest.RESOURCESECRET);
        prop.setProperty("havre.oauth.url", bifrostUrl);
        ConfigurationProperties.setProperties(prop);

        RestAssured.config = RestAssuredConfig.config()
                .sslConfig(SSLConfig.sslConfig().with()
                        .keystore(ca.keyStore, TempCert.KS_PASSWD)
                        .allowAllHostnames())
                .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
                        .gsonObjectMapperFactory(new GOMF()));
    }

    static String testDeploymentSecret()
    {
        return "d5beeb631f223a644a32ca343d9da6de";
    }

    public void setUp() throws Exception
    {
        when(ps.newPrefix_(any(SOKID.class), anyString())).thenReturn(pf);
        when(localUser.get()).thenReturn(user);
        when(localDID.get()).thenReturn(did);

        when(tm.begin_()).thenReturn(t);
        when(tokenManager.acquire_(any(Cat.class), anyString())).thenReturn(tk);
        when(tk.pseudoPause_(anyString())).thenReturn(tcb);

        when(csdb.isCollectingContent_(any(SIndex.class))).thenReturn(true);

        when(ccsub.waitSubmitted_(any(SOID.class))).thenReturn(new Future<Long>() {
            @Override public boolean cancel(boolean interrupt) { return false; }
            @Override public boolean isCancelled() { return false; }
            @Override public boolean isDone() { return true; }
            @Override public Long get() { return null; }
            @Override public Long get(long timeout, TimeUnit unit) { return null; }
        });
        when(casub.waitSubmitted_(any(SOID.class))).thenReturn(new Future<Void>() {
            @Override public boolean cancel(boolean interrupt) { return false; }
            @Override public boolean isCancelled() { return false; }
            @Override public boolean isDone() { return true; }
            @Override public Void get() { return null; }
            @Override public Void get(long timeout, TimeUnit unit) { return null; }
        });

        // start local gateway
        if (useProxy) {
            IPrivateKeyProvider kmgr = mock(IPrivateKeyProvider.class);
            when(kmgr.getCert()).thenReturn(gateway.cert);
            when(kmgr.getPrivateKey()).thenReturn(gateway.key);
            havre = new Havre(UserID.DUMMY, new DID(UniqueID.ZERO), kmgr, kmgr, cacert,
                    getGlobalTimer(), tokenVerifier,  null);
            havre.start();

            prop.setProperty("api.tunnel.port", Integer.toString(havre.getTunnelPort()));
        }
    }

    private static class GOMF implements GsonObjectMapperFactory
    {
        @Override
        @SuppressWarnings("rawtypes")
        public Gson create(Class cls, String charset)
        {
            return new GsonBuilder()
                    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        }
    }

    @After
    public void tearDown() throws Exception
    {
        service.stop();
        inj.getInstance(CoreScheduler.class).shutdown();
        if (useProxy) {
            havre.stop();
            tunnel.stop();
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

    protected static String json(Object o) { return _gson.toJson(o); }

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
                    protected void configure() {
                        bind(SPBlockingClient.Factory.class).toInstance(factSP);
                    }
                });

        BifrostTest.createTestEntities(user, inj);

        return inj;
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
}
