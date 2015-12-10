package com.aerofs.daemon.rest;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.RestObject;
import com.aerofs.bifrost.server.BifrostTest;
import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.activity.OutboundEventLogger;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.IPathResolver;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.migration.ImmigrantCreator;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.mock.logical.MockDS.MockDSDir;
import com.aerofs.daemon.core.mock.logical.MockDS.MockDSObject;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase;
import com.aerofs.daemon.core.protocol.ContentProvider;
import com.aerofs.daemon.core.protocol.DaemonContentProvider;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.lib.db.ICollectorStateDatabase;
import com.aerofs.daemon.lib.db.IDID2UserDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.rest.handler.DaemonRestContentHelper;
import com.aerofs.daemon.rest.handler.RestContentHelper;
import com.aerofs.daemon.rest.resources.AbstractResource;
import com.aerofs.daemon.rest.resources.ChildrenResource;
import com.aerofs.daemon.rest.resources.FilesMetadataResource;
import com.aerofs.daemon.rest.resources.FoldersResource;
import com.aerofs.daemon.rest.util.EntityTagUtil;
import com.aerofs.ids.MDID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.*;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.oauth.VerifyTokenResponse;
import com.aerofs.tunnel.ITunnelConnectionListener;
import com.aerofs.tunnel.TunnelAddress;
import com.aerofs.tunnel.TunnelHandler;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.util.Timer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.slf4j.Logger;

import javax.ws.rs.core.EntityTag;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.aerofs.base.TimerUtil.getGlobalTimer;
import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;


/**
 * Base class for tests exercising the public REST API
 */
@RunWith(Parameterized.class)
public class AbstractRestTest extends BaseAbstractRestTest
{
    @Parameters
    public static Collection<Object[]> data()
    {
        return Arrays.asList(new Object[][] {{false}, {true}});
    }

    protected static final Logger l = Loggers.getLogger(AbstractRestTest.class);

    protected @Mock DirectoryService ds;
    protected @Mock NativeVersionControl nvc;
    protected @Mock ObjectCreator oc;
    protected @Mock ObjectDeleter od;
    protected @Mock VersionUpdater vu;
    protected @Mock Expulsion expulsion;
    protected ObjectMover om;
    protected @Mock ImmigrantCreator ic;

    protected @Mock OutboundEventLogger oel;
    protected @Mock DaemonContentProvider provider;
    protected @Mock RemoteLinkDatabase rldb;

    public AbstractRestTest(boolean useProxy)
    {
        super(useProxy);
    }

    protected MockDS mds;

    @BeforeClass
    public static void commonSetup() throws Exception
    {
        BaseAbstractRestTest.commonSetup();
    }

    @Before
    public void setUp() throws Exception
    {
        super.setUp();
        mds = new MockDS(rootSID, ds, sm, sm, ss);
        mds.root();  // setup sid<->sidx mapping for root..

        om = new ObjectMover();
        om.inject_(vu, ds, expulsion);
        om = spy(om);

        when(ds.getCAHash_(any(SOKID.class))).thenReturn(new ContentHash(CONTENT_HASH));
        when(ic.move_(any(SOID.class), any(SOID.class), anyString(), any(PhysicalOp.class), eq(t)))
                .thenAnswer(invocation -> {
                    SOID soid = (SOID) invocation.getArguments()[0];
                    SOID soidToParent = (SOID) invocation.getArguments()[1];
                    String toName = (String) invocation.getArguments()[2];
                    PhysicalOp op = (PhysicalOp) invocation.getArguments()[3];
                    if (soidToParent.sidx().equals(soid.sidx())) {
                        om.moveInSameStore_(soid, soidToParent.oid(), toName, op, true,
                                t);
                        return soid;
                    } else {
                        return ic.createLegacyImmigrantRecursively_(ds.resolve_(soid).parent(), soid,
                                soidToParent, toName, op, t);
                    }
                });

        doNothing().when(vu).update_(any(SOCKID.class), any(Trans.class));
        // start REST service

        inj = coreInjector();
        Set<AbstractResource> resources = Sets.newHashSet(inj.getInstance(ChildrenResource.class),
                inj.getInstance(FilesMetadataResource.class), inj.getInstance(FoldersResource.class));

        service = new RestService(inj, kmgr, resources) {
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

            // waiter for connection establishment
            final SettableFuture<Void> future = SettableFuture.create();
            havre.setTunnelConnectionListener(new ITunnelConnectionListener() {
                @Override
                public void tunnelOpen(TunnelAddress addr, TunnelHandler handler)
                {
                    future.set(null);
                }

                @Override
                public void tunnelClosed(TunnelAddress addr, TunnelHandler handler) {}
            });

            // open tunnel between gateway and rest service
            tunnel = new RestTunnelClient(localUser, localDID, getGlobalTimer(),
                    clientSslEngineFactory, service);
            tunnel.start().awaitUninterruptibly();

            // wait for connection to be established on gateway side (version handshake)
            future.get(10, TimeUnit.SECONDS);
        }
    }

    protected EntityTag etagForMeta(SOID soid) throws SQLException
    {
        return inj.getInstance(EntityTagUtil.class).etagForMeta(soid);
    }

    private Injector coreInjector()
    {
        final IIMCExecutor imce = mock(IIMCExecutor.class);

        Injector inj = Guice.createInjector(new TestTokenVerifierModule(), new RestModule(), new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind(CfgLocalUser.class).toInstance(localUser);
                bind(CfgCACertificateProvider.class).toInstance(cacert);
                bind(StoreHierarchy.class).toInstance(ss);
                bind(NativeVersionControl.class).toInstance(nvc);
                bind(DirectoryService.class).toInstance(ds);
                bind(LocalACL.class).toInstance(acl);
                bind(IMapSID2SIndex.class).toInstance(sm);
                bind(IMapSIndex2SID.class).toInstance(sm);
                bind(IDID2UserDatabase.class).toInstance(did2user);
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
                bind(ICollectorStateDatabase.class).toInstance(csdb);
                bind(CentralVersionDatabase.class).toInstance(_cvdb);
                bind(IVersionUpdater.class).toInstance(vu);
                bind(CfgUsePolaris.class).toInstance(usePolaris);
                bind(IPathResolver.class).toInstance(ds);
                bind(RestContentHelper.class).to(DaemonRestContentHelper.class);
                bind(ContentProvider.class).toInstance(provider);
                bind(RemoteLinkDatabase.class).toInstance(rldb);
            }
        });

        // wire event handlers (no queue, events are immediately executed)
        ICoreEventHandlerRegistrar reg = inj.getInstance(RestCoreEventHandlerRegistar.class);
        final CoreEventDispatcher disp = new CoreEventDispatcher(Collections.singleton(reg));
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            disp.dispatch_((IEvent)args[0], (Prio)args[1]);
            return null;
        }).when(imce).execute_(any(IEvent.class), any(Prio.class));

        return inj;
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

    protected RequestSpecification givenLinkShareReadAccess() throws Exception
    {
        return givenTokenWithScopes(ImmutableSet.of("files.read", "linksharing"));
    }

    protected RequestSpecification givenReadAccess() throws Exception
    {
        return givenTokenWithScopes(ImmutableSet.of("files.read"));
    }

    protected RequestSpecification givenReadAccessTo(RestObject object) throws Exception
    {
        return givenTokenWithScopes(ImmutableSet.of("files.read:" + object.toStringFormal()));
    }

    protected RequestSpecification givenLinkShareReadAccessTo(RestObject object) throws Exception
    {
        return givenTokenWithScopes(ImmutableSet.of("files.read:" + object.toStringFormal(), "linksharing"));
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

        when(oc.createMeta_(eq(type), eq(p), eq(name), eq(PhysicalOp.APPLY), eq(t)))
                .thenAnswer(invocation -> {
                    MockDSDir pp = mds.cd(ds.resolve_(p));
                    if (pp.hasChild(name)) throw new ExAlreadyExist();
                    MockDSObject o;
                    switch (type) {
                    case FILE:
                        o = pp.file(name, 0);
                        break;
                    case DIR:
                        o = pp.dir(name);
                        break;
                    case ANCHOR:
                        o = pp.anchor(name);
                        break;
                    default:
                        throw new AssertionError();
                    }
                    soid.set(o.soid());
                    return o.soid();
                });

        return soid;
    }

    protected SettableFuture<SOID> whenMove(String objectName, String parentName, String newName)
            throws Exception
    {
        final SOID parentSoid = ds.resolveFollowAnchorThrows_(Path.fromString(rootSID, parentName));
        final SOID objectSoid = ds.resolveThrows_(Path.fromString(rootSID, objectName));
        final SettableFuture<SOID> soid = SettableFuture.create();
        doAnswer(invocation -> {
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
        }).when(ds).setOAParentAndName_(any(OA.class), any(OA.class), anyString(), eq(t));
        when(ic.createLegacyImmigrantRecursively_(any(ResolvedPath.class), eq(objectSoid), eq(parentSoid),
                eq(newName), eq(PhysicalOp.APPLY), eq(t)))
                .thenAnswer(invocation -> {
                    Object[] args = invocation.getArguments();
                    SOID objectSoid1 = (SOID)args[1];
                    SOID parentSoid1 = (SOID)args[2];
                    String pathFrom = ds.resolve_(objectSoid1).toStringRelative();
                    String pathTo = ds.resolve_(parentSoid1).append((String)args[3]).toStringRelative();
                    if (mds.resolve(new Path(rootSID, pathTo.split("/"))) == null) {
                        // does not exist yet
                        mds.move(pathFrom, pathTo, t);
                        SOID r = new SOID(parentSoid1.sidx(), objectSoid1.oid());
                        soid.set(r);
                        return r;
                    } else { // already exists
                        throw new ExAlreadyExist();
                    }
                });

        return soid;
    }
}
