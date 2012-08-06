package com.aerofs.daemon.core.syncstatus;

import com.aerofs.daemon.core.ActivityLog;
import com.aerofs.daemon.core.ds.DirectoryService;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.mock.logical.MockAnchor;
import com.aerofs.daemon.core.mock.logical.MockDir;
import com.aerofs.daemon.core.mock.logical.MockFile;
import com.aerofs.daemon.core.mock.logical.MockRoot;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.update.DPUTUpdateSchemaForSyncStatus;
import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.ActivityLogDatabase;
import com.aerofs.daemon.lib.db.IActivityLogDatabase.ModifiedObject;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.MetaDatabase;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.Path;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.CfgBuildType;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.lib.syncstat.SyncStatBlockingClient;
import com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType;
import com.aerofs.proto.Sp.PBSyncStatNotification;
import com.aerofs.proto.Syncstat.*;
import com.aerofs.proto.Syncstat.GetSyncStatusReply.DeviceSyncStatus;
import com.aerofs.proto.Syncstat.GetSyncStatusReply.SyncStatus;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.lib.db.IActivityLogDatabase;
import com.aerofs.daemon.lib.db.ISyncStatusDatabase;
import com.aerofs.daemon.lib.db.SyncStatusDatabase;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.testlib.AbstractTest;

import java.net.URL;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class TestSyncStatusSynchronizer extends AbstractTest
{
    @Mock Trans t;
    @Mock CoreQueue q;
    @Mock CoreScheduler sched;
    @Mock TC tc;
    @Mock TCB tcb;
    @Mock Token tk;
    @Mock TransManager tm;
    @Mock NativeVersionControl nvc;
    @Mock StoreCreator sc;
    @Mock SIDMap sm;
    @Mock MapSIndex2Store sidx2s;
    @Mock DevicePresence dp;
    @Mock DirectoryService ds;
    @Mock SyncStatBlockingClient ssc;
    @Mock SyncStatBlockingClient.Factory ssf;
    @Mock IPhysicalStorage ps;
    @Mock MapAlias2Target alias2target;
    @Mock IStores stores;
    @Mock ActivityLog al;
    @Mock CfgBuildType buildType;

    InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
    IMetaDatabase mdb = new MetaDatabase(dbcw.mockCoreDBCW());
    IStoreDatabase sdb = new StoreDatabase(dbcw.mockCoreDBCW());
    ISyncStatusDatabase db = new SyncStatusDatabase(dbcw.mockCoreDBCW());
    IActivityLogDatabase aldb = new ActivityLogDatabase(dbcw.mockCoreDBCW());
    MapSIndex2DeviceBitMap sidx2dbm = new MapSIndex2DeviceBitMap(sdb);

    LocalSyncStatus lsync;
    SyncStatusSynchronizer sync;

    MockRoot root =
        new MockRoot(
            new MockFile("f1", 2),
            new MockDir("d2",
                new MockFile("f2.2"),
                new MockAnchor("a2.3",
                    new MockDir("d2.3.1"),
                    new MockFile("f2.3.2"),
                    new MockAnchor("a2.3.3")
                )
            )
        );

    SOID o_r, o_f1, o_d2, o_f22, o_a23, o_d231, o_f232, o_a233;

    final DID d0 = new DID(UniqueID.generate());
    final DID d1 = new DID(UniqueID.generate());
    final DID d2 = new DID(UniqueID.generate());

    /**
     * Synchronizer creation need to be manually placed within test case due to the presence of a
     * startup phase (bootstrap, scan, pull)
     */
    private void createSynchronizer()
    {
        // Create synchronizer with mix of mocks and real objects operating on mock DB
        sync = new SyncStatusSynchronizer(q, sched, tc, tm, lsync, ds, ssf, sm, sm,
                sidx2dbm, al, aldb, nvc, buildType);
    }

    private SOID resolve(String s) throws Exception
    {
        SOID soid = ds.resolveNullable_(Path.fromString(s));
        Assert.assertNotNull(soid);
        Assert.assertNotNull(soid.sidx());
        Assert.assertNotNull(soid.oid());
        Assert.assertNotNull(sm.get_(soid.sidx()));
        return soid;
    }

    private void assertSyncStatusEquals(BitVector expected, SOID... soids) throws Exception
    {
        for (SOID soid : soids) Assert.assertEquals(new BitVector(), mdb.getSyncStatus_(soid));
    }

    @Before
    public void setup() throws Exception
    {
        dbcw.init_();

        // stub object hierarchy
        root.mock(ds, sm, sm, sidx2s, sdb, mdb);

        // get SOIDs for all objects
        o_r = resolve("");
        o_f1 = resolve("f1");
        o_d2 = resolve("d2");
        o_f22 = resolve("d2/f2.2");
        o_a23 = resolve("d2/a2.3");
        o_d231 = resolve("d2/a2.3/d2.3.1");
        o_f232 = resolve("d2/a2.3/f2.3.2");
        o_a233 = resolve("d2/a2.3/a2.3.3");

        // TODO(huguesb): remove this hack when sync stat is enabled on prod
        when(buildType.isStaging()).thenReturn(true);

        when(nvc.getAllLocalVersions_(any(SOCID.class))).thenReturn(new Version());

        when(tm.begin_()).thenReturn(t);
        when(ssf.create(any(URL.class), any(String.class))).thenReturn(ssc);

        // Stub thread control
        when(tc.acquireThrows_(any(Cat.class), any(String.class))).thenReturn(tk);
        when(tk.pseudoPause_(any(String.class))).thenReturn(tcb);

        // Stub queue will simply execute incoming events upon addition
        Answer<Object> qa = new Answer<Object>()
        {
            @Override
            public Object answer(InvocationOnMock invocation)
                    throws Throwable
            {
                Object[] args = invocation.getArguments();
                assert args[0] instanceof AbstractEBSelfHandling;
                ((AbstractEBSelfHandling) args[0]).handle_();
                return true;
            }
        };
        doAnswer(qa).when(q).enqueueBlocking(any(IEvent.class), any(Prio.class));
        doAnswer(qa).when(q).enqueueBlocking_(any(IEvent.class), any(Prio.class));
        doAnswer(qa).when(q).enqueue(any(IEvent.class), any(Prio.class));
        doAnswer(qa).when(q).enqueue_(any(IEvent.class), any(Prio.class));

        // forward DirectoryService mock to in-memory db
        doAnswer(new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                Object[] args = invocation.getArguments();
                if (args[0] instanceof SOID && args[1] instanceof BitVector) {
                    mdb.setSyncStatus_((SOID)args[0], (BitVector)args[1], t);
                }
                return null;
            }
        }).when(ds).setSyncStatus_(any(SOID.class), any(BitVector.class), any(Trans.class));

        when(ds.getSyncStatus_(any(SOID.class))).thenAnswer(new Answer<BitVector>()
        {
            @Override
            public BitVector answer(InvocationOnMock invocation)
                    throws Throwable
            {
                Object[] args = invocation.getArguments();
                return (args[0] instanceof SOID) ? mdb.getSyncStatus_((SOID)args[0]) : null;
            }
        });

        lsync = new LocalSyncStatus(ds, db, sidx2dbm);

        sidx2dbm.addDevice_(o_r.sidx(), d0, t);

        // check device lists before pull
        Assert.assertEquals(Integer.valueOf(0), sidx2dbm.getDeviceMapping_(o_r.sidx()).get(d0));
        Assert.assertNull(sidx2dbm.getDeviceMapping_(o_r.sidx()).get(d1));
        Assert.assertNull(sidx2dbm.getDeviceMapping_(o_r.sidx()).get(d2));

        Assert.assertNull(sidx2dbm.getDeviceMapping_(o_f232.sidx()).get(d0));
        Assert.assertNull(sidx2dbm.getDeviceMapping_(o_f232.sidx()).get(d1));
        Assert.assertNull(sidx2dbm.getDeviceMapping_(o_f232.sidx()).get(d2));

        // check sync status before pull
        assertSyncStatusEquals(new BitVector(), o_f1, o_d2, o_f22, o_a23, o_d231, o_f232, o_a233);
    }

    @After
    public void tearDown() throws Exception
    {
        dbcw.fini_();
    }

    @Test
    public void shouldNotSchedulePullOnOlderEpoch() throws Exception
    {
        lsync.setPullEpoch_(42, t);
        createSynchronizer();
        verify(ssc).getSyncStatus(Long.valueOf(42));        // startup

        sync.notificationReceived_(PBSyncStatNotification.newBuilder().setSsEpoch(40).build());

        verify(ssc).getSyncStatus(anyLong());               // no extra pull
    }

    @Test
    public void shouldNotSchedulePullOnCurrentEpoch() throws Exception
    {
        lsync.setPullEpoch_(42, t);
        createSynchronizer();
        verify(ssc).getSyncStatus(Long.valueOf(42));        // startup

        sync.notificationReceived_(PBSyncStatNotification.newBuilder().setSsEpoch(42).build());

        verify(ssc).getSyncStatus(anyLong());               // no extra pull
    }

    @Test
    public void shouldSchedulePullOnNewerEpochSingleMessage() throws Exception
    {
        lsync.setPullEpoch_(42, t);
        createSynchronizer();
        verify(ssc).getSyncStatus(Long.valueOf(42));        // startup

        // stub RPC call
        GetSyncStatusReply reply = GetSyncStatusReply.newBuilder()
                .setSsEpoch(43)
                .setMore(false)
                .addSyncStatuses(SyncStatus.newBuilder()
                        .setSid(sm.get_(o_f1.sidx()).toPB())
                        .setOid(o_f1.oid().toPB())
                        .addDevices(
                                DeviceSyncStatus.newBuilder().setDid(d0.toPB()).setIsSynced(false))
                        .addDevices(
                                DeviceSyncStatus.newBuilder().setDid(d1.toPB()).setIsSynced(true))
                        .build())
                .addSyncStatuses(SyncStatus.newBuilder()
                        .setSid(sm.get_(o_d2.sidx()).toPB())
                        .setOid(o_d2.oid().toPB())
                        .addDevices(DeviceSyncStatus.newBuilder()
                                .setDid(d0.toPB())
                                .setIsSynced(true))
                        .addDevices(DeviceSyncStatus.newBuilder()
                                .setDid(d1.toPB())
                                .setIsSynced(true))
                        .build())
                .addSyncStatuses(SyncStatus.newBuilder()
                        .setSid(sm.get_(o_f232.sidx()).toPB())
                        .setOid(o_f232.oid().toPB())
                        .addDevices(DeviceSyncStatus.newBuilder()
                                .setDid(d2.toPB())
                                .setIsSynced(true))
                        .build())
                .build();
        when(ssc.getSyncStatus(Long.valueOf(42))).thenReturn(reply);

        // pull
        sync.notificationReceived_(PBSyncStatNotification.newBuilder().setSsEpoch(43).build());
        verify(ssc, times(2)).getSyncStatus(anyLong()); // extra pull

        // check value of epoch after pull
        Assert.assertEquals(43, lsync.getPullEpoch_());

        // check device lists after pull
        Assert.assertEquals(Integer.valueOf(0), sidx2dbm.getDeviceMapping_(o_r.sidx()).get(d0));
        Assert.assertEquals(Integer.valueOf(1), sidx2dbm.getDeviceMapping_(o_r.sidx()).get(d1));
        Assert.assertNull(sidx2dbm.getDeviceMapping_(o_r.sidx()).get(d2));

        Assert.assertNull(sidx2dbm.getDeviceMapping_(o_f232.sidx()).get(d0));
        Assert.assertNull(sidx2dbm.getDeviceMapping_(o_f232.sidx()).get(d1));
        Assert.assertEquals(Integer.valueOf(0), sidx2dbm.getDeviceMapping_(o_f232.sidx()).get(d2));

        // check sync status after pull
        Assert.assertEquals(new BitVector(false, true), mdb.getSyncStatus_(o_f1));
        Assert.assertEquals(new BitVector(true, true), mdb.getSyncStatus_(o_d2));
        Assert.assertEquals(new BitVector(true), mdb.getSyncStatus_(o_f232));
        assertSyncStatusEquals(new BitVector(), o_r, o_f22, o_a23, o_d231, o_a233);
    }

    @Test
    public void shouldSchedulePullOnNewerEpochMultiMessage() throws Exception
    {
        lsync.setPullEpoch_(42, t);
        createSynchronizer();
        verify(ssc, times(1)).getSyncStatus(Long.valueOf(42)); // startup

        GetSyncStatusReply reply = GetSyncStatusReply.newBuilder()
                .setSsEpoch(45)
                .setMore(true)
                .addSyncStatuses(SyncStatus.newBuilder()
                        .setSid(sm.get_(o_f22.sidx()).toPB())
                        .setOid(o_f22.oid().toPB())
                        .addDevices(
                                DeviceSyncStatus.newBuilder().setDid(d2.toPB()).setIsSynced(true))
                        .addDevices(
                                DeviceSyncStatus.newBuilder().setDid(d1.toPB()).setIsSynced(true))
                        .build())
                .build();
        when(ssc.getSyncStatus(Long.valueOf(42))).thenReturn(reply);

        reply = GetSyncStatusReply.newBuilder()
                .setSsEpoch(46)
                .setMore(false)
                .addSyncStatuses(SyncStatus.newBuilder()
                        .setSid(sm.get_(o_d231.sidx()).toPB())
                        .setOid(o_d231.oid().toPB())
                        .addDevices(DeviceSyncStatus.newBuilder()
                                .setDid(d0.toPB())
                                .setIsSynced(false))
                        .addDevices(DeviceSyncStatus.newBuilder()
                                .setDid(d2.toPB())
                                .setIsSynced(true))
                        .build())
                .build();
        when(ssc.getSyncStatus(Long.valueOf(45))).thenReturn(reply);

        // pull
        sync.notificationReceived_(PBSyncStatNotification.newBuilder().setSsEpoch(43).build());
        verify(ssc, times(3)).getSyncStatus(any(Long.class)); // extra pulls

        // check value of epoch after pull
        Assert.assertEquals(46, lsync.getPullEpoch_());

        // check device lists after pull
        Assert.assertEquals(Integer.valueOf(0), sidx2dbm.getDeviceMapping_(o_r.sidx()).get(d0));
        Assert.assertEquals(Integer.valueOf(2), sidx2dbm.getDeviceMapping_(o_r.sidx()).get(d1));
        Assert.assertEquals(Integer.valueOf(1), sidx2dbm.getDeviceMapping_(o_r.sidx()).get(d2));

        Assert.assertEquals(Integer.valueOf(0), sidx2dbm.getDeviceMapping_(o_d231.sidx()).get(d0));
        Assert.assertNull(sidx2dbm.getDeviceMapping_(o_d231.sidx()).get(d1));
        Assert.assertEquals(Integer.valueOf(1), sidx2dbm.getDeviceMapping_(o_d231.sidx()).get(d2));

        // check sync status after pull
        Assert.assertEquals(new BitVector(false, true, true), mdb.getSyncStatus_(o_f22));
        Assert.assertEquals(new BitVector(false, true), mdb.getSyncStatus_(o_d231));
        assertSyncStatusEquals(new BitVector(), o_r, o_f1, o_d2, o_a23, o_f232, o_a233);
    }

    @Test
    public void shouldFastForward() throws SQLException
    {
        lsync.setPullEpoch_(42, t);
        createSynchronizer();
        sync.notificationReceived_(PBSyncStatNotification.newBuilder()
                .setSsEpoch(43)
                .setStatus(SyncStatus.newBuilder()
                        .setSid(sm.get_(o_f1.sidx()).toPB())
                        .setOid(o_f1.oid().toPB())
                        .addDevices(DeviceSyncStatus.newBuilder()
                                .setDid(d0.toPB())
                                .setIsSynced(true)))
                .build());

    }

    @Test
    public void shouldNotFastForward() throws SQLException
    {
        lsync.setPullEpoch_(42, t);
        createSynchronizer();
        sync.notificationReceived_(PBSyncStatNotification.newBuilder()
                .setSsEpoch(44)
                .setStatus(SyncStatus.newBuilder()
                        .setSid(sm.get_(o_f1.sidx()).toPB())
                        .setOid(o_f1.oid().toPB())
                        .addDevices(DeviceSyncStatus.newBuilder()
                                .setDid(d0.toPB())
                                .setIsSynced(true)))
                .build());

    }

    // TODO (huguesb): add test case for unknown SID
    // TODO (huguesb): add test case for known SID / unknown OID
    // TODO (huguesb): add test case for expelled SID/OID

    private void assertBootstrapSeq(SOID... expected) throws SQLException
    {
        IDBIterator<SOID> it = lsync.getBootstrapSOIDs_();
        try {
            for (SOID soid : expected) {
                Assert.assertTrue(it.next_());
                Assert.assertEquals(soid, it.get_());
            }
            Assert.assertTrue(!it.next_());
        } finally {
            it.close_();
        }
    }

    private void checkSVHcalls(SOID... expected) throws Exception
    {
        for (SOID soid : expected) {
            ByteString pbsid = sm.get_(soid.sidx()).toPB();
            ByteString pboid = soid.oid().toPB();
            verify(ssc, times(1)).setVersionHash(eq(pboid), eq(pbsid), any(ByteString.class));
        }
    }

    private void addBootstrapSOIDs(SOID... soids) throws SQLException
    {
        DPUTUpdateSchemaForSyncStatus.addBootstrapSOIDs(dbcw.getConnection(),
                                                        Lists.newArrayList(soids));
    }

    @Test
    public void shouldPushVersionForBootstrap() throws Exception
    {
        addBootstrapSOIDs(o_f1, o_d2, o_f232);

        // check state of bootstrap table before startup
        assertBootstrapSeq(o_f1, o_d2, o_f232);

        // startup
        createSynchronizer();

        // check calls made during bootstrap sequence

        checkSVHcalls(o_f1, o_d2, o_f232);

        // check state of bootstrap table after startup
        assertBootstrapSeq();
    }

    private void assertActivitySeq(SOID... expected) throws SQLException
    {
        IDBIterator<ModifiedObject> it = aldb.getModifiedObjects_(lsync.getPushEpoch_());
        try {
            for (SOID soid : expected) {
                Assert.assertTrue(it.next_());
                ModifiedObject mo = it.get_();
                Assert.assertEquals(soid, mo._soid);
            }
            Assert.assertTrue(!it.next_());
        } finally {
            it.close_();
        }
    }

    @Test
    public void shouldPushVersionForActivityOnStartup() throws Exception
    {
        lsync.setPullEpoch_(0, t);
        Set<DID> dids = new HashSet<DID>();
        dids.add(d0);

        aldb.addActivity_(o_f1, ActivityType.CREATION.getNumber(),
                Path.fromString("f1"), null, dids, t);
        aldb.addActivity_(o_d2, ActivityType.CREATION.getNumber(),
                Path.fromString("d2"), null, dids, t);

        assertActivitySeq(o_f1, o_d2);

        createSynchronizer();

        checkSVHcalls(o_f1, o_d2);

        Assert.assertEquals(2, lsync.getPushEpoch_());
        assertActivitySeq();
    }

    @Test
    public void shouldPushVersionForNewActivity() throws Exception
    {
        // TODO (huguesb): need more complex mocking of transaction manager...
    }
}
