package com.aerofs.daemon.core.syncstatus;

import com.aerofs.daemon.core.ActivityLog;
import com.aerofs.daemon.core.ds.DirectoryService;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyByte;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.byteThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
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
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
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
import com.google.common.collect.Sets;
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
    @Mock SyncStatusConnection ssc;
    @Mock IPhysicalStorage ps;
    @Mock MapAlias2Target alias2target;
    @Mock IStores stores;
    @Mock ActivityLog al;
    @Mock CfgLocalUser localUser;

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
        sync = new SyncStatusSynchronizer(q, sched, tc, tm, lsync, ds, ssc, sm, sm,
                sidx2dbm, al, aldb, nvc, localUser);
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
        for (SOID soid : soids) Assert.assertEquals(expected, mdb.getSyncStatus_(soid));
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
        when(localUser.get()).thenReturn("someone@aerofs.com");

        // SyncStatusSynchronizer calls this to compute the version hash of an object
        // these tests do not care about the actual value of the version vector, just that it
        // isn't null (the default of Mockito, which causes NPE...)
        when(nvc.getAllLocalVersions_(any(SOCID.class))).thenReturn(new Version());

        when(tm.begin_()).thenReturn(t);

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
        verify(ssc).getSyncStatus_(42);        // startup

        sync.notificationReceived_(PBSyncStatNotification.newBuilder().setSsEpoch(40).build());

        verify(ssc).getSyncStatus_(anyLong());               // no extra pull
    }

    @Test
    public void shouldNotSchedulePullOnCurrentEpoch() throws Exception
    {
        lsync.setPullEpoch_(42, t);
        createSynchronizer();
        verify(ssc).getSyncStatus_(42);        // startup

        sync.notificationReceived_(PBSyncStatNotification.newBuilder().setSsEpoch(42).build());

        verify(ssc).getSyncStatus_(anyLong());               // no extra pull
    }

    @Test
    public void shouldSchedulePullOnNewerEpochSingleMessage() throws Exception
    {
        lsync.setPullEpoch_(42, t);
        createSynchronizer();
        verify(ssc).getSyncStatus_(42);        // startup

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
        when(ssc.getSyncStatus_(42)).thenReturn(reply);

        // pull
        sync.notificationReceived_(PBSyncStatNotification.newBuilder().setSsEpoch(43).build());
        verify(ssc, times(2)).getSyncStatus_(anyLong()); // extra pull

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
        verify(ssc, times(1)).getSyncStatus_(42); // startup

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
        when(ssc.getSyncStatus_(42)).thenReturn(reply);

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
        when(ssc.getSyncStatus_(45)).thenReturn(reply);

        // pull
        sync.notificationReceived_(PBSyncStatNotification.newBuilder().setSsEpoch(43).build());
        verify(ssc, times(3)).getSyncStatus_(anyLong()); // extra pulls

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
            Assert.assertFalse(it.next_());
        } finally {
            it.close_();
        }
    }

    private void checkSVHcalls(SOID... expected) throws Exception
    {
        for (SOID soid : expected) {
            SID sid = sm.get_(soid.sidx());
            OID oid = soid.oid();
            verify(ssc, times(1)).setVersionHash_(eq(oid), eq(sid), any(byte[].class));
        }
    }

    /**
     * The bootstrap table is populated once by a post update task so there is no public API
     * to add entries to it.
     *
     * see {@link DPUTUpdateSchemaForSyncStatus}
     **/
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

    @Test
    public void shouldIgnoreBootstrapForNonExistingStore() throws Exception
    {
        SOID dummy = new SOID(new SIndex(42), new OID(UniqueID.generate()));
        addBootstrapSOIDs(dummy);

        // check state of bootstrap table before startup
        assertBootstrapSeq(dummy);

        // startup
        createSynchronizer();

        // check calls made during bootstrap sequence
        verify(ssc, never()).setVersionHash_(any(OID.class), any(SID.class), any(byte[].class));

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
            Assert.assertFalse(it.next_());
        } finally {
            it.close_();
        }
    }

    @Test
    public void shouldPushVersionForActivityOnStartup() throws Exception
    {
        // fill activity log table
        Set<DID> dids = Sets.newHashSet();
        dids.add(d0);
        aldb.addActivity_(o_f1, ActivityType.CREATION.getNumber(),
                Path.fromString("f1"), null, dids, t);
        aldb.addActivity_(o_d2, ActivityType.CREATION.getNumber(),
                Path.fromString("d2"), null, dids, t);

        // check state of activity log table before startup
        assertActivitySeq(o_f1, o_d2);

        // startup
        createSynchronizer();

        // check that the version hash are pushed through the sync stat client
        checkSVHcalls(o_f1, o_d2);

        // check that push epoch was increased past the two existing activity log entries
        Assert.assertEquals(2, lsync.getPushEpoch_());
        assertActivitySeq();
    }

    @Test
    public void shouldIgnoreActivityForNonExistingStore() throws Exception
    {
        // add an invalid SOID to the activity log table
        Set<DID> dids = Sets.newHashSet();
        dids.add(d0);
        SOID dummy = new SOID(new SIndex(42), new OID(UniqueID.generate()));
        aldb.addActivity_(dummy, ActivityType.CREATION.getNumber(),
                Path.fromString("d2"), null, dids, t);

        // check state of activity log table before startup
        assertActivitySeq(dummy);

        // startup
        createSynchronizer();

        // check that no version hash is pushed for invalid SOID
        verify(ssc, never()).setVersionHash_(any(OID.class), any(SID.class), any(byte[].class));

        // check that push epoch was increased past invalid SOID
        Assert.assertEquals(1, lsync.getPushEpoch_());
        assertActivitySeq();
    }

    @Test
    public void shouldPushVersionForNewActivity() throws Exception
    {
        // TODO (huguesb): need more complex mocking of transaction manager...
    }
}
