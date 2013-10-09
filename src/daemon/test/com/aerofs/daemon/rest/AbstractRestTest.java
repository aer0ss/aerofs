package com.aerofs.daemon.rest;

import com.aerofs.base.Loggers;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SOID;
import com.aerofs.testlib.AbstractTest;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.jayway.restassured.RestAssured;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Properties;
import java.util.TimeZone;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base class for tests exercising the public REST API
 */
public class AbstractRestTest extends AbstractTest
{
    protected static final Logger l = Loggers.getLogger(AbstractRestTest.class);

    protected @Mock DirectoryService ds;
    protected @Mock LocalACL acl;
    protected @Mock SIDMap sm;
    protected @Mock IStores ss;
    private @Mock CfgLocalUser localUser;
    protected @Mock NativeVersionControl nvc;
    private @Mock CfgKeyManagersProvider kmgr;

    private static TempCert ca;
    private static TempCert client;

    protected static final UserID user = UserID.fromInternal("foo@bar.baz");
    protected static final DID did = DID.generate();

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

    protected static DateFormat ISO_8601 = utcFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

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

        when(kmgr.getCert()).thenReturn(client.cert);
        when(kmgr.getPrivateKey()).thenReturn(client.key);

        final IIMCExecutor imce = mock(IIMCExecutor.class);

        // inject mock objects into service
        Injector inj = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure()
            {
                bind(CfgLocalUser.class).toInstance(localUser);
                bind(IStores.class).toInstance(ss);
                bind(NativeVersionControl.class).toInstance(nvc);
                bind(DirectoryService.class).toInstance(ds);
                bind(LocalACL.class).toInstance(acl);
                bind(IMapSID2SIndex.class).toInstance(sm);
                bind(IMapSIndex2SID.class).toInstance(sm);
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

        Properties prop = new Properties();
        prop.setProperty("rest.port", "0");
        ConfigurationProperties.setProperties(prop);

        // start REST service
        service = new RestService(inj, kmgr);
        service.start();
        RestAssured.baseURI = "https://localhost";
        RestAssured.port = service.getListeningPort();
    }

    @After
    public void tearDown() throws Exception
    {
        service.stop();
    }

    protected RestObject object(String path) throws SQLException
    {
        SOID soid = ds.resolveNullable_(Path.fromString(rootSID, path));
        assertNotNull(path, soid);
        SID sid = sm.get_(soid.sidx());
        return new RestObject(sid, soid.oid());
    }
}
