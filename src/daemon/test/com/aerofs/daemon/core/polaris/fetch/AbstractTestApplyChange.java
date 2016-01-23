package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.TimerUtil;
import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.acl.EffectiveUserList;
import com.aerofs.daemon.core.activity.OutboundEventLogger;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.*;
import com.aerofs.daemon.core.expel.AbstractLogicalStagingArea;
import com.aerofs.daemon.core.expel.LogicalStagingArea;
import com.aerofs.daemon.core.migration.IEmigrantDetector;
import com.aerofs.daemon.core.migration.IEmigrantTargetSIDLister;
import com.aerofs.daemon.core.migration.ImmigrantDetector;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserPathResolver;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserStoreHierarchy;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.*;
import com.aerofs.daemon.core.polaris.InMemoryDS;
import com.aerofs.daemon.core.polaris.PolarisClient;
import com.aerofs.daemon.core.polaris.api.RemoteChange;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.*;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase.MetaChange;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase.RemoteContent;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.daemon.core.protocol.*;
import com.aerofs.daemon.core.quota.IQuotaEnforcement;
import com.aerofs.daemon.core.quota.NullQuotaEnforcement;
import com.aerofs.daemon.core.store.*;
import com.aerofs.daemon.core.transfers.download.Downloads;
import com.aerofs.daemon.core.transfers.download.IContentDownloads;
import com.aerofs.daemon.lib.db.*;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.lib.db.ver.IPrefixVersionDatabase;
import com.aerofs.daemon.lib.db.ver.PrefixVersionDatabase;
import com.aerofs.daemon.transport.lib.IRoundTripTimes;
import com.aerofs.daemon.transport.lib.RoundTripTimes;
import com.aerofs.ids.*;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.*;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.InMemoryCoreDBCW;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ssmp.SSMPConnection;
import com.aerofs.testlib.AbstractBaseTest;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.*;
import com.google.inject.internal.Scoping;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jboss.netty.util.Timer;
import org.junit.After;
import org.junit.Before;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractTestApplyChange extends AbstractBaseTest {
    static {
        // Change to DEBUG if you're writing a test, but keep at NONE otherwise.
        //LogUtil.setLevel(Level.DEBUG);
        LogUtil.enableConsoleLogging();
    }

    protected final CfgUsePolaris usePolaris = new CfgUsePolaris() {
        @Override public boolean get() { return true; }
    };

    protected final InMemoryCoreDBCW dbcw = new InMemoryCoreDBCW(mock(InjectableDriver.class));

    protected final UserID user = UserID.fromInternal("foo@bar.baz");
    protected final SID rootSID = SID.rootSID(user);
    private final DID did = DID.generate();

    protected RemoteLinkDatabase rldb;
    protected MetaBufferDatabase mbdb;
    protected CentralVersionDatabase cvdb;
    protected ContentChangesDatabase ccdb;
    protected RemoteContentDatabase rcdb;
    protected MapAlias2Target a2t;
    protected MetaChangesDatabase mcdb;

    final Trans t = mock(Trans.class);

    protected IPhysicalStorage ps = setupPhysicalStorage();
    protected final Map<SOID, IPhysicalFile> files = new HashMap<>();

    protected Injector inj;

    protected InMemoryDS mds;
    protected DirectoryServiceImpl ds;
    protected TransManager tm;

    protected ObjectCreator oc;
    protected ObjectMover om;
    protected ObjectDeleter od;

    protected ApplyChange ac;

    protected SIndex sidx;

    protected final PolarisClient client = mock(PolarisClient.class);
    protected final PolarisClient.Factory factClient = mock(PolarisClient.Factory.class);

    protected IPhysicalStorage setupPhysicalStorage() {
        // FIXME: in-memory fs
        IPhysicalStorage ps = mock(IPhysicalStorage.class);
        final IPhysicalFolder folder = mock(IPhysicalFolder.class);
        final IPhysicalFile file = mock(IPhysicalFile.class);
        final IPhysicalPrefix prefix = mock(IPhysicalPrefix.class);
        try {
            when(ps.newFolder_(any(ResolvedPath.class))).thenReturn(folder);
            when(ps.newPrefix_(any(SOKID.class), anyString())).thenReturn(prefix);
            when(prefix.newOutputStream_(anyBoolean())).thenAnswer(invocation ->
                    new PrefixOutputStream(new ByteArrayOutputStream(), BaseSecUtil.newMessageDigest())
            );
            when(ps.newFile_(any(ResolvedPath.class), any(KIndex.class))).thenAnswer(invocation -> {
                ResolvedPath p = (ResolvedPath) invocation.getArguments()[0];
                IPhysicalFile pf = files.get(p.soid());
                return pf != null ? pf : file;
            });
        } catch (Exception e) {
            throw new Error(e);
        }
        return ps;
    }

    @Before
    public void setUp() throws Exception
    {
        doAnswer(invocation -> {
            Object[] arg = invocation.getArguments();
            try {
                ((AsyncTaskCallback) arg[2]).onSuccess_(false);
            } catch (Throwable t) {
                ((AsyncTaskCallback) arg[2]).onFailure_(t);
            }
            return null;
        }).when(client).post(anyString(), anyObject(), any(AsyncTaskCallback.class), any(),
                any(Executor.class));

        when(factClient.create()).thenReturn(client);

        inj = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);
                binder().disableCircularProxies();

                // FIXME: config
                bind(ICfgStore.class).toInstance(mock(CfgDatabase.class));
                bind(CfgUsePolaris.class).toInstance(usePolaris);
                bind(CfgLocalUser.class).toInstance(new CfgLocalUser() {
                    @Override public UserID get() { return user; }
                });
                bind(CfgRootSID.class).toInstance(new CfgRootSID() {
                    @Override public SID get() { return rootSID; }
                });
                bind(CfgLocalDID.class).toInstance(new CfgLocalDID() {
                    @Override public DID get() { return did; }
                });

                bind(IDBCW.class).toInstance(dbcw);

                bind(InjectableDriver.class).toInstance(mock(InjectableDriver.class));
                bind(OutboundEventLogger.class).toInstance(mock(OutboundEventLogger.class));
                bind(Causality.class).toInstance(mock(Causality.class));
                bind(ContentSender.class).toInstance(mock(ContentSender.class));
                bind(ContentReceiver.class).toInstance(mock(ContentReceiver.class));
                bind(ContentProvider.class).toInstance(mock(ContentProvider.class));
                bind(IEmigrantTargetSIDLister.class).toInstance(mock(IEmigrantTargetSIDLister.class));
                bind(IEmigrantDetector.class).toInstance(mock(IEmigrantDetector.class));
                bind(ImmigrantDetector.class).toInstance(mock(ImmigrantDetector.class));
                bind(NativeVersionControl.class).toInstance(mock(NativeVersionControl.class));
                bind(NewUpdatesSender.class).toInstance(mock(NewUpdatesSender.class));
                bind(Downloads.class).toInstance(mock(Downloads.class));
                bind(Transports.class).toInstance(mock(Transports.class));
                bind(Devices.class).toInstance(mock(Devices.class));
                bind(SSMPConnection.class).toInstance(mock(SSMPConnection.class));
                bind(PolarisClient.class).toInstance(mock(PolarisClient.class));

                bind(DirectoryService.class).to(DirectoryServiceImpl.class);
                bind(ObjectSurgeon.class).to(DirectoryServiceImpl.class);
                bind(IPathResolver.class).to(DirectoryServiceImpl.class);

                bind(IRoundTripTimes.class).to(RoundTripTimes.class);
                bind(IMapSIndex2SID.class).to(SIDMap.class);
                bind(IMapSID2SIndex.class).to(SIDMap.class);
                bind(IMetaDatabase.class).to(MetaDatabase.class);
                bind(IMetaDatabaseWalker.class).to(MetaDatabase.class);
                bind(IAliasDatabase.class).to(AliasDatabase.class);
                bind(ICollectorSequenceDatabase.class).to(CollectorSequenceDatabase.class);
                bind(IPrefixVersionDatabase.class).to(PrefixVersionDatabase.class);
                bind(IExpulsionDatabase.class).to(ExpulsionDatabase.class);
                bind(ISIDDatabase.class).to(SIDDatabase.class);
                bind(IStoreDatabase.class).to(StoreDatabase.class);
                bind(IACLDatabase.class).to(ACLDatabase.class);
                bind(IActivityLogDatabase.class).to(ActivityLogDatabase.class);
                bind(IDID2UserDatabase.class).to(DID2UserDatabase.class);
                bind(IUserAndDeviceNameDatabase.class).to(UserAndDeviceNameDatabase.class);
                bind(ICollectorFilterDatabase.class).to(CollectorFilterDatabase.class);
                bind(ISenderFilterDatabase.class).to(SenderFilterDatabase.class);
                bind(IPulledDeviceDatabase.class).to(PulledDeviceDatabase.class);

                bind(StoreHierarchy.class).to(SingleuserStoreHierarchy.class);
                bind(AbstractPathResolver.Factory.class).to(SingleuserPathResolver.Factory.class);

                bind(IStoreJoiner.class).toInstance(mock(IStoreJoiner.class));

                bind(IQuotaEnforcement.class).to(NullQuotaEnforcement.class);

                bind(IVersionUpdater.class).to(VersionUpdater.class);
                bind(Store.Factory.class).to(DaemonPolarisStore.Factory.class);
                bind(AbstractLogicalStagingArea.class).to(LogicalStagingArea.class);
                bind(ApplyChange.Impl.class).to(ApplyChangeImpl.class);
                bind(IContentDownloads.class).to(Downloads.class);
                bind(ContentFetcherIterator.Filter.class).to(DefaultFetchFilter.class);
                bind(IContentVersionControl.class).to(PolarisContentVersionControl.class);

                bind(Timer.class).toInstance(TimerUtil.getGlobalTimer());

                bind(EffectiveUserList.class).toInstance(mock(EffectiveUserList.class));
            }

            @Provides
            public PolarisClient.Factory provideClientFactory() { return factClient; }

            @Provides
            public IOSUtil provideIOSUtil()
            {
                return OSUtil.get();
            }

            @Provides
            public IPhysicalStorage providePS() { return ps; }

        });

        dbcw.init_();
        try (Statement s = dbcw.getConnection().createStatement()) {
            new PolarisSchema().create_(s, dbcw);
        }
        dbcw.commit_();
        inj.getInstance(Stores.class).init_();
        try {
            inj.getInstance(StoreCreator.class).createRootStore_(rootSID, "", mock(Trans.class));
        } catch (Exception e) { throw new AssertionError(e); }
        sidx = inj.getInstance(IMapSID2SIndex.class).get_(rootSID);

        ac = inj.getInstance(ApplyChange.class);
        ds = inj.getInstance(DirectoryServiceImpl.class);

        rldb = inj.getInstance(RemoteLinkDatabase.class);
        mbdb = inj.getInstance(MetaBufferDatabase.class);
        cvdb = inj.getInstance(CentralVersionDatabase.class);
        ccdb = inj.getInstance(ContentChangesDatabase.class);
        rcdb = inj.getInstance(RemoteContentDatabase.class);
        a2t = inj.getInstance(MapAlias2Target.class);
        mcdb = inj.getInstance(MetaChangesDatabase.class);

        tm = inj.getInstance(TransManager.class);

        oc = inj.getInstance(ObjectCreator.class);
        om = inj.getInstance(ObjectMover.class);
        od = inj.getInstance(ObjectDeleter.class);

        mds = new InMemoryDS(inj);
    }

    @After
    public void tearDown() throws Exception
    {
        inj.getInstance(CoreScheduler.class).shutdown();
        dbcw.fini_();
    }

    protected static class PolarisState
    {
        Map<SIndex, Map<UniqueID, Long>> versions = Maps.newHashMap();
        List<RemoteChange> changes = Lists.newArrayList();

        void add(SIndex sidx, RemoteChange rc)
        {
            Map<UniqueID, Long> vv = get(sidx);
            long v = vv.getOrDefault(rc.oid, 0L) + 1;
            vv.put(rc.oid, v);
            rc.newVersion = v;
            rc.logicalTimestamp = changes.size() + 1;
            changes.add(rc);
        }

        void add(SIndex sidx, RemoteChange... rcl)
        {
            for (RemoteChange rc : rcl) add(sidx, rc);
        }

        void preShare(SIndex sidxFrom, SIndex sidxTo, OID... oids) {
            for (OID oid : oids) {
                Long v = get(sidxFrom).remove(oid);
                if (v != null) {
                    // NB: offset is to counteract offset in #add()
                    // as this is used to ensure versions are preserved when sharing
                    get(sidxTo).put(oid, v - 1);
                }
            }
        }

        Map<UniqueID, Long> get(SIndex sidx) {
            Map<UniqueID, Long> vv = versions.get(sidx);
            if (vv == null) {
                vv = new HashMap<>();
                versions.put(sidx, vv);
            }
            return vv;
        }
    }

    protected final PolarisState state = new PolarisState();

    protected void apply(SIndex sidx, RemoteChange... changes) throws Exception
    {
        int min = state.changes.size();
        state.add(sidx, changes);
        long maxLTS = state.changes.size();
        try (Trans t = tm.begin_()) {
            for (RemoteChange rc : state.changes.subList(min, state.changes.size())) {
                ac.apply_(sidx, rc, maxLTS, t);
            }
            t.commit_();
        }
    }

    protected void apply(RemoteChange... changes) throws Exception
    {
        apply(sidx, changes);
    }

    protected static Matcher<OA> isAt(OID parent, String name, OA.Type type)
    {
        return new BaseMatcher<OA>()
        {
            @Override
            public boolean matches(Object o)
            {
                return o != null && o instanceof OA
                        && parent.equals(((OA)o).parent())
                        && name.equals(((OA)o).name())
                        && type == ((OA)o).type();
            }

            @Override
            public void describeTo(Description description)
            {
                description.appendText("at(").appendValue(parent).appendValue(name).appendText(")");
            }
        };
    }

    protected void assertOAEquals(OID oid, OID parent, String name, OA.Type type) throws SQLException
    {
        assertThat(ds.getOANullable_(new SOID(sidx, oid)), isAt(parent, name, type));
    }

    protected void assertIsBuffered(boolean yes, OID... oids) throws SQLException
    {
        for (OID o : oids) assertEquals(o.toString(), yes, mbdb.isBuffered_(new SOID(sidx, o)));
    }

    protected void assertNotPresent(SIndex sidx, OID... oids) throws SQLException
    {
        for (OID o : oids) assertNull(ds.getOANullable_(new SOID(sidx, o)));
    }

    protected void assertHasRemoteLink(SIndex sidx, OID oid, OID parent, String name, long logicalTimestamp)
            throws SQLException
    {
        assertEquals(new RemoteLink(parent,name, logicalTimestamp), rldb.getParent_(sidx, oid));
    }

    protected void assertNotPresent(OID... oids) throws SQLException
    {
        assertNotPresent(sidx, oids);
    }

    protected void assertHasRemoteLink(OID oid, OID parent, String name, long logicalTimestamp)
            throws SQLException
    {
        assertHasRemoteLink(sidx, oid, parent, name, logicalTimestamp);
    }

    protected void assertHasLocalChanges(SIndex sidx, MetaChange... changes) throws SQLException {
        System.out.println("verifying meta changes");
        expect(changes, mcdb.getChangesSince_(sidx, -1), (expected, actual) -> {
            assertEquals(expected.oid, actual.oid);
            assertEquals(expected.newParent, actual.newParent);
            assertEquals(expected.newName, actual.newName);
        });
    }

    protected <T, U> void expect(T[] expected, IDBIterator<U> iterator, BiConsumer<T, U> check)
            throws SQLException {
        try (IDBIterator<U> it = iterator) {
            int i = 0;
            boolean failed = false;
            while (it.next_()) {
                U actual = it.get_();
                System.out.println("found: " + String.valueOf(actual));
                if (i >= expected.length) {
                    System.out.println("unexpected: " + String.valueOf(actual));
                    failed = true;
                } else {
                    System.out.println("expected: " + String.valueOf(expected[i]));
                    check.accept(expected[i], actual);
                    ++i;
                }
            }
            if (i < expected.length) {
                while (i < expected.length) {
                    System.out.println("expected but missing: " + String.valueOf(expected[i++]));
                }
                failed = true;
            }
            if (failed) fail();
        }
    }

    protected OID oidAt(String path) throws SQLException {
        SOID soid = ds.resolveNullable_(Path.fromString(rootSID, path));
        return soid != null ? soid.oid() : null;
    }

    protected void assertAliased(SIndex sidx, OID alias, OID target) throws SQLException {
        assertEquals(new SOID(sidx, target), a2t.dereferenceAliasedOID_(new SOID(sidx, alias)));
    }

    protected void assertHasContentChanges(SIndex sidx, OID... changed)
            throws SQLException {
        System.out.println("verifying content changes");
        expect(changed, ccdb.getChanges_(sidx), (expected, actual) -> {
            assertEquals(expected, actual.oid);
        });
    }

    protected void assertHasRemoteContent(SIndex sidx, OID oid, RemoteContent... contents)
            throws SQLException {
        System.out.println("verifying remote content");
        expect(contents, rcdb.list_(sidx, oid), (expected, actual) -> {
            assertEquals(expected.version, actual.version);
            assertEquals(expected.originator, actual.originator);
            assertEquals(expected.hash, actual.hash);
            assertEquals(expected.length, actual.length);
        });
    }

    protected void setContent(SIndex sidx, OID oid, byte[] c, long mtime, Trans t)
            throws SQLException, IOException {
        ds.createCA_(new SOID(sidx, oid), KIndex.MASTER, t);
        ds.setCA_(new SOKID(sidx, oid, KIndex.MASTER), c.length, mtime,
                new ContentHash(BaseSecUtil.hash(c)), t);

        ccdb.insertChange_(sidx, oid, t);
        mockPhy(sidx, oid, KIndex.MASTER, c, mtime, t);
    }

    protected void mockPhy(SIndex sidx, OID oid, KIndex kidx, byte[] c, long mtime, Trans t)
            throws SQLException, IOException
    {
        IPhysicalFile pf = mock(IPhysicalFile.class);
        when(pf.lengthOrZeroIfNotFile()).thenReturn((long)c.length);
        when(pf.lastModified()).thenReturn(mtime);
        when(pf.newInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(c));

        files.put(new SOID(sidx, oid), pf);
    }

    protected void downloadContent(SIndex sidx, OID oid, long version, byte[] d, long mtime, Trans t)
            throws SQLException, IOException {
        KIndex kidx = KIndex.MASTER;
        OA oa = ds.getOA_(new SOID(sidx, oid));
        if (oa.caMasterNullable() != null) kidx = kidx.increment();

        ds.createCA_(new SOID(sidx, oid), kidx, t);
        ds.setCA_(new SOKID(sidx, oid, kidx), d.length, mtime, new ContentHash(BaseSecUtil.hash(d)), t);
        cvdb.setVersion_(sidx, oid, version, t);

        mockPhy(sidx, oid, kidx, d, mtime, t);
    }
}
