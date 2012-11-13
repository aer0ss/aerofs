package com.aerofs.daemon.core.syncstatus;

import com.aerofs.daemon.core.ActivityLog;
import com.aerofs.daemon.core.ds.DirectoryService;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.store.DeviceBitMap;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.IActivityLogDatabase.ModifiedObject;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.proto.Sp.PBSyncStatNotification;
import com.aerofs.proto.SyncStatus.GetSyncStatusReply;
import com.aerofs.proto.SyncStatus.GetSyncStatusReply.DeviceSyncStatus;
import com.aerofs.proto.SyncStatus.GetSyncStatusReply.DevicesSyncStatus;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.lib.db.IActivityLogDatabase;
import com.aerofs.daemon.lib.db.ISyncStatusDatabase;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.testlib.AbstractTest;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
    @Mock SyncStatusConnection ssc;
    @Mock ActivityLog al;
    @Mock CfgLocalUser localUser;

    @Mock DirectoryService ds;
    @Mock SIDMap sm;
    @Mock MapSIndex2DeviceBitMap sidx2dbm;
    @Mock ISyncStatusDatabase ssdb;
    @Mock IActivityLogDatabase aldb;
    @Mock LocalSyncStatus lsync;

    @InjectMocks MockDS mds;

    // used by LocalSyncStatus mock
    List<SOID> bootStrap;

    // used by ActivityLogDatabase mock
    List<ModifiedObject> modified;

    SyncStatusSynchronizer sync;

    SOID o_r, o_f1, o_d2, o_f22, o_a23, o_d231, o_f232, o_a233;

    // remote DIDs
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
        for (SOID soid : soids) Assert.assertEquals(expected, ds.getSyncStatus_(soid));
    }

    private void assertBootstrapSeqEquals(SOID... expected) throws SQLException
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

    private static class IsSetEqualTo<T> extends ArgumentMatcher<List<T>>
    {
        private final Set<T> _expected;

        public IsSetEqualTo(Iterable<T> expected)
        {
            _expected = Sets.newHashSet(expected);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean matches(Object argument)
        {
            List<T> c = (List<T>)argument;
            Util.l(this).info("setEq: " + c + " [" + _expected + "]");
            return _expected.equals(Sets.newHashSet(c));
        }
    }

    private static <T> List<T> setEq(Iterable<T> expected)
    {
        return argThat(new IsSetEqualTo<T>(expected));
    }

    private void verifySetVersionHashInvocations(SOID... expected) throws Exception
    {
        Map<SIndex, List<ByteString>> chunks = Maps.newHashMap();
        for (SOID soid : expected) {
            List<ByteString> l = chunks.get(soid.sidx());
            if (l == null) {
                l = Lists.newArrayList();
                chunks.put(soid.sidx(), l);
            }
            l.add(soid.oid().toPB());
        }

        for (Entry<SIndex, List<ByteString>> e : chunks.entrySet()) {
            SID sid = sm.get_(e.getKey());
            verify(ssc, times(1)).setVersionHash_(eq(sid), setEq(e.getValue()),
                    anyListOf(ByteString.class), anyLong());
        }
    }

    /**
     * The bootstrap table is populated once by a post update task so there is no public API
     * to add entries to it.
     **/
    private void setBootstrapSOIDs(SOID... soids)
    {
        bootStrap = Lists.newArrayList(soids);
    }

    private void addActivityLog(SOID... soids)
    {
        long epoch = modified.isEmpty() ? 0 : modified.get(modified.size() - 1)._idx;
        for (SOID soid : soids) {
            ModifiedObject mo = new ModifiedObject(++epoch, soid);
            modified.add(mo);
        }
    }

    @Before
    public void setup() throws Exception
    {
        // stub object hierarchy
        mds.dids(d0).root()
                .file("f1").parent()
                .dir("d2")
                        .file("f2.2").parent()
                        .anchor("a2.3")
                                .dir("d2.3.1").parent()
                                .file("f2.3.2").parent()
                                .anchor("a2.3.3");

        // get SOIDs for all objects
        o_r = mds.root().soid();
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
        when(nvc.getLocalVersion_(any(SOCKID.class))).thenReturn(new Version());

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

        // mock LocalSyncStatus
        bootStrap = Lists.newArrayList();
        when(lsync.getPushEpoch_()).thenReturn(0L);
        when(lsync.getPullEpoch_()).thenReturn(0L);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                Long pushEpoch = (Long)invocation.getArguments()[0];
                when(lsync.getPushEpoch_()).thenReturn(pushEpoch);
                return null;
            }
        }).when(lsync).setPushEpoch_(anyLong(), any(Trans.class));
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                Long pullEpoch = (Long)invocation.getArguments()[0];
                when(lsync.getPullEpoch_()).thenReturn(pullEpoch);
                return null;
            }
        }).when(lsync).setPullEpoch_(anyLong(), any(Trans.class));
        when(lsync.getBootstrapSOIDs_()).thenAnswer(new Answer<IDBIterator<SOID>>() {
            @Override
            public IDBIterator<SOID> answer(InvocationOnMock invocation) throws Throwable
            {
                return new IDBIterator<SOID>() {
                    int _idx = -1;
                    boolean _closed = false;

                    @Override
                    public SOID get_() throws SQLException
                    {
                        assert !_closed;
                        return bootStrap.get(_idx);
                    }

                    @Override
                    public boolean next_() throws SQLException
                    {
                        assert !_closed;
                        return ++_idx < bootStrap.size();
                    }

                    @Override
                    public void close_() throws SQLException
                    {
                        _closed = true;
                    }

                    @Override
                    public boolean closed_()
                    {
                        return _closed;
                    }
                };
            }
        });
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                SOID soid = (SOID)invocation.getArguments()[0];
                bootStrap.remove(soid);
                return null;
            }
        }).when(lsync).removeBootsrapSOID_(any(SOID.class), any(Trans.class));

        // update mock MapSIndex2DeviceBitMap reactively
        when(sidx2dbm.addDevice_(any(SIndex.class), any(DID.class), any(Trans.class)))
                .thenAnswer(new Answer<Integer>() {
                    @Override
                    public Integer answer(InvocationOnMock invocation) throws Throwable
                    {
                        Object[] args = invocation.getArguments();
                        SIndex sidx = (SIndex) args[0];
                        DID did = (DID) args[1];
                        DeviceBitMap dbm = sidx2dbm.getDeviceMapping_(sidx);
                        return dbm.addDevice_(did);
                    }
                });

        // mock activity log db
        modified = Lists.newArrayList();
        when(aldb.getModifiedObjects_(anyLong())).thenAnswer(new Answer<IDBIterator<ModifiedObject>>()
        {
            @Override
            public IDBIterator<ModifiedObject> answer(InvocationOnMock invocation) throws Throwable
            {
                long base = (Long)invocation.getArguments()[0];
                // locate first entry in the modified object list whose index is higher than base
                int index = 0;
                while (index < modified.size() && modified.get(index)._idx <= base) { ++index; }
                final int first = index;
                // build db iterator
                return new IDBIterator<ModifiedObject>() {
                    int _idx = first - 1;
                    boolean _closed = false;

                    @Override
                    public ModifiedObject get_() throws SQLException
                    {
                        assert !_closed;
                        return modified.get(_idx);
                    }

                    @Override
                    public boolean next_() throws SQLException
                    {
                        assert !_closed;
                        return ++_idx < modified.size();
                    }

                    @Override
                    public void close_() throws SQLException
                    {
                        _closed = true;
                    }

                    @Override
                    public boolean closed_()
                    {
                        return _closed;
                    }
                };
            }
        });

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

    }

    @Test
    public void shouldNotSchedulePullOnOlderEpoch() throws Exception
    {
        lsync.setPullEpoch_(42, t);
        createSynchronizer();

        sync.notificationReceived_(PBSyncStatNotification.newBuilder().setSsEpoch(40).build());

        verify(ssc, never()).getSyncStatus_(anyLong());               // no pull
    }

    @Test
    public void shouldNotSchedulePullOnCurrentEpoch() throws Exception
    {
        lsync.setPullEpoch_(42, t);
        createSynchronizer();

        sync.notificationReceived_(PBSyncStatNotification.newBuilder().setSsEpoch(42).build());

        verify(ssc, never()).getSyncStatus_(anyLong());               // no pull
    }

    @Test
    public void shouldSchedulePullOnNewerEpochSingleMessage() throws Exception
    {
        lsync.setPullEpoch_(42, t);
        createSynchronizer();

        // stub RPC call
        GetSyncStatusReply reply = GetSyncStatusReply.newBuilder()
                .setServerEpoch(43)
                .setMore(false)
                .addSyncStatuses(DevicesSyncStatus.newBuilder()
                        .setSid(sm.get_(o_f1.sidx()).toPB())
                        .setOid(o_f1.oid().toPB())
                        .addDevices(
                                DeviceSyncStatus.newBuilder().setDid(d0.toPB()).setIsSynced(false))
                        .addDevices(
                                DeviceSyncStatus.newBuilder().setDid(d1.toPB()).setIsSynced(true))
                        .build())
                .addSyncStatuses(DevicesSyncStatus.newBuilder()
                        .setSid(sm.get_(o_d2.sidx()).toPB())
                        .setOid(o_d2.oid().toPB())
                        .addDevices(DeviceSyncStatus.newBuilder()
                                .setDid(d0.toPB())
                                .setIsSynced(true))
                        .addDevices(DeviceSyncStatus.newBuilder()
                                .setDid(d1.toPB())
                                .setIsSynced(true))
                        .build())
                .addSyncStatuses(DevicesSyncStatus.newBuilder()
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
        verify(ssc).getSyncStatus_(anyLong()); // pull

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
        Assert.assertEquals(new BitVector(false, true), ds.getSyncStatus_(o_f1));
        Assert.assertEquals(new BitVector(true, true), ds.getSyncStatus_(o_d2));
        Assert.assertEquals(new BitVector(true), ds.getSyncStatus_(o_f232));
        assertSyncStatusEquals(new BitVector(), o_r, o_f22, o_a23, o_d231, o_a233);
    }

    @Test
    public void shouldSchedulePullOnNewerEpochMultiMessage() throws Exception
    {
        lsync.setPullEpoch_(42, t);
        createSynchronizer();

        GetSyncStatusReply reply = GetSyncStatusReply.newBuilder()
                .setServerEpoch(45)
                .setMore(true)
                .addSyncStatuses(DevicesSyncStatus.newBuilder()
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
                .setServerEpoch(46)
                .setMore(false)
                .addSyncStatuses(DevicesSyncStatus.newBuilder()
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
        verify(ssc, times(2)).getSyncStatus_(anyLong()); // extra pulls

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
        Assert.assertEquals(new BitVector(false, true, true), ds.getSyncStatus_(o_f22));
        Assert.assertEquals(new BitVector(false, true), ds.getSyncStatus_(o_d231));
        assertSyncStatusEquals(new BitVector(), o_r, o_f1, o_d2, o_a23, o_f232, o_a233);
    }

    @Test
    public void shouldFastForward() throws SQLException
    {
        lsync.setPullEpoch_(42, t);
        createSynchronizer();
        sync.notificationReceived_(PBSyncStatNotification.newBuilder()
                .setSsEpoch(43)
                .setStatus(DevicesSyncStatus.newBuilder()
                        .setSid(sm.get_(o_f1.sidx()).toPB())
                        .setOid(o_f1.oid().toPB())
                        .addDevices(
                                DeviceSyncStatus.newBuilder().setDid(d0.toPB()).setIsSynced(true)))
                .build());

    }

    @Test
    public void shouldNotFastForward() throws SQLException
    {
        lsync.setPullEpoch_(42, t);
        createSynchronizer();
        sync.notificationReceived_(PBSyncStatNotification.newBuilder()
                .setSsEpoch(44)
                .setStatus(DevicesSyncStatus.newBuilder()
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

    @Test
    public void shouldPushVersionForBootstrap() throws Exception
    {
        setBootstrapSOIDs(o_f1, o_d2, o_f232);

        // check state of bootstrap table before startup
        assertBootstrapSeqEquals(o_f1, o_d2, o_f232);

        // startup
        createSynchronizer();

        // check calls made during bootstrap sequence
        verifySetVersionHashInvocations(o_f1, o_d2, o_f232);

        // check state of bootstrap table after startup
        assertBootstrapSeqEquals();
    }

    @Test
    public void shouldIgnoreBootstrapForNonExistingStore() throws Exception
    {
        SOID dummy = new SOID(new SIndex(42), new OID(UniqueID.generate()));
        setBootstrapSOIDs(dummy);

        // check state of bootstrap table before startup
        assertBootstrapSeqEquals(dummy);

        // startup
        createSynchronizer();

        // check calls made during bootstrap sequence
        verify(ssc, never()).setVersionHash_(
                any(SID.class), anyListOf(ByteString.class), anyListOf(ByteString.class), anyLong());

        // check state of bootstrap table after startup
        assertBootstrapSeqEquals();
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
        addActivityLog(o_f1, o_d2);

        // check state of activity log table before startup
        assertActivitySeq(o_f1, o_d2);

        // startup
        createSynchronizer();

        // check that the version hash are pushed through the sync stat client
        verifySetVersionHashInvocations(o_f1, o_d2);

        // check that push epoch was increased past the two existing activity log entries
        Assert.assertEquals(2, lsync.getPushEpoch_());
        assertActivitySeq();
    }

    @Test
    public void shouldIgnoreActivityForNonExistingStore() throws Exception
    {
        // add an invalid SOID to the activity log table
        SOID dummy = new SOID(new SIndex(42), new OID(UniqueID.generate()));
        addActivityLog(dummy);

        // check state of activity log table before startup
        assertActivitySeq(dummy);

        // startup
        createSynchronizer();

        // check that no version hash is pushed for invalid SOID
        verify(ssc, never()).setVersionHash_(
                any(SID.class), anyListOf(ByteString.class), anyListOf(ByteString.class), anyLong());

        // check that push epoch was increased past invalid SOID
        Assert.assertEquals(1, lsync.getPushEpoch_());
        assertActivitySeq();
    }

    @Test
    public void shouldPushVersionForNewActivity() throws Exception
    {
        // TODO (huguesb): need more complex mocking of transaction manager...
    }

    @Test
    public void shouldIgnoreSignInEpochEqualsPushEpoch() throws Exception
    {
        lsync.setPushEpoch_(42, t);
        createSynchronizer();
        sync.onSignIn_(42);

        Assert.assertEquals(42, lsync.getPushEpoch_());
    }

    @Test
    public void shouldIgnoreSignInEpochGreaterThanPushEpoch() throws Exception
    {
        lsync.setPushEpoch_(42, t);
        createSynchronizer();
        sync.onSignIn_(256);

        Assert.assertEquals(42, lsync.getPushEpoch_());
    }

    @Test
    public void shouldRollbackPushEpochIfSignInEpochLower() throws Exception
    {
        lsync.setPushEpoch_(42, t);
        createSynchronizer();
        sync.onSignIn_(7);

        Assert.assertEquals(7, lsync.getPushEpoch_());
    }
}
