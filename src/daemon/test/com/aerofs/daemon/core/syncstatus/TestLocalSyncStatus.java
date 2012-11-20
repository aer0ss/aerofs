package com.aerofs.daemon.core.syncstatus;

import java.sql.SQLException;

import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.linker.IgnoreList;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.store.DescendantStores;
import com.aerofs.daemon.core.store.DeviceBitMap;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStoreDeletionListener.StoreDeletionNotifier;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserPathResolver;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserStores;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.id.UniqueID;
import com.google.common.collect.Lists;
import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.ISyncStatusDatabase;
import com.aerofs.daemon.lib.db.MetaDatabase;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.SyncStatusDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.testlib.AbstractTest;

import static org.mockito.Mockito.when;

public class TestLocalSyncStatus extends AbstractTest
{
    @Mock Trans t;
    @Mock TransManager tm;
    @Mock StoreCreator sc;
    @Mock SIDMap sm;
    @Mock MapSIndex2Store sidx2s;
    @Mock DevicePresence dp;
    @Mock IPhysicalStorage ps;
    @Mock MapAlias2Target alias2target;
    @Mock IStores stores;
    @Mock DescendantStores dss;
    @Mock IMapSIndex2SID sidx2sid;
    @Mock AggregateSyncStatus assc;
    @Mock IgnoreList il;
    @Mock FrequentDefectSender fds;
    @Mock StoreDeletionNotifier sdn;
    @Mock SingleuserStores sss;

    InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
    IMetaDatabase mdb = new MetaDatabase(dbcw.mockCoreDBCW());
    IStoreDatabase sdb = new StoreDatabase(dbcw.mockCoreDBCW());
    ISyncStatusDatabase ssdb = new SyncStatusDatabase(dbcw.mockCoreDBCW());
    MapSIndex2DeviceBitMap sidx2dbm = new MapSIndex2DeviceBitMap(sdb);

    LocalSyncStatus lsync;

    final SIndex sidx = new SIndex(1);
    final DID d0 = new DID(UniqueID.generate());
    final DID d1 = new DID(UniqueID.generate());
    final DID d2 = new DID(UniqueID.generate());

    final SOID o1 = new SOID(sidx, new OID(UniqueID.generate()));
    final SOID o2 = new SOID(sidx, new OID(UniqueID.generate()));
    final SOID o3 = new SOID(sidx, new OID(UniqueID.generate()));

    @Before
    public void setup() throws Exception
    {
        dbcw.init_();

        when(tm.begin_()).thenReturn(t);

        DirectoryService ds = new DirectoryService();
        SingleuserPathResolver pathResolver = new SingleuserPathResolver(sss, ds, sidx2sid);
        ds.inject_(ps, mdb, alias2target, tm, sm, il, fds, sdn, pathResolver);
        lsync = new LocalSyncStatus(ds, ssdb, sidx2dbm, assc, sdn, dss);
    }

    @After
    public void tearDown() throws Exception
    {
        dbcw.fini_();
    }

    private void assertDeviceList(DID... expected) throws SQLException
    {
        DeviceBitMap dbm = sidx2dbm.getDeviceMapping_(sidx);
        for (int i = 0; i < expected.length; ++i) {
            Assert.assertTrue(i < dbm.size());
            Assert.assertEquals(expected[i], dbm.get(i));
            Assert.assertEquals(i, dbm.get(expected[i]).intValue());
        }
        Assert.assertEquals(expected.length, dbm.size());
    }

    @Test
    public void shouldAddDevices() throws SQLException
    {
        sdb.add_(sidx, t);

        assertDeviceList();

        sidx2dbm.addDevice_(sidx, d0, t);
        assertDeviceList(d0);

        sidx2dbm.addDevice_(sidx, d1, t);
        assertDeviceList(d0, d1);

        sidx2dbm.addDevice_(sidx, d2, t);
        assertDeviceList(d0, d1, d2);
    }

    @Test
    public void shouldUpdateSyncStatus() throws SQLException, ExAlreadyExist
    {
        sdb.add_(sidx, t);
        mdb.createOA_(sidx, OID.ROOT, OID.ROOT, "R", OA.Type.DIR, 0, t);
        mdb.createOA_(sidx, o1.oid(), OID.ROOT, "foo", OA.Type.FILE, 0, t);
        mdb.createOA_(sidx, o2.oid(), OID.ROOT, "bar", OA.Type.DIR, 0, t);
        mdb.createOA_(sidx, o3.oid(), o2.oid(), "bar", OA.Type.FILE, 0, t);

        Assert.assertEquals(0, mdb.getSyncStatus_(o1).size());
        Assert.assertEquals(0, mdb.getSyncStatus_(o2).size());
        Assert.assertEquals(0, mdb.getSyncStatus_(o3).size());

        Assert.assertEquals(new BitVector(), mdb.getSyncStatus_(o1));
        Assert.assertEquals(new BitVector(), mdb.getSyncStatus_(o2));
        Assert.assertEquals(new BitVector(), mdb.getSyncStatus_(o3));

        sidx2dbm.addDevice_(sidx, d0, t);
        sidx2dbm.addDevice_(sidx, d1, t);
        sidx2dbm.addDevice_(sidx, d2, t);

        Assert.assertEquals(0, mdb.getSyncStatus_(o1).size());
        Assert.assertEquals(0, mdb.getSyncStatus_(o2).size());
        Assert.assertEquals(0, mdb.getSyncStatus_(o3).size());

        Assert.assertEquals(new BitVector(), mdb.getSyncStatus_(o1));
        Assert.assertEquals(new BitVector(), mdb.getSyncStatus_(o2));
        Assert.assertEquals(new BitVector(), mdb.getSyncStatus_(o3));

        mdb.setSyncStatus_(o1, new BitVector(3, true), t);

        Assert.assertEquals(new BitVector(3, true), mdb.getSyncStatus_(o1));
        Assert.assertEquals(new BitVector(3, false), mdb.getSyncStatus_(o2));
        Assert.assertEquals(new BitVector(), mdb.getSyncStatus_(o3));
    }

    @Test
    public void shouldUpdatePullEpoch() throws SQLException
    {
        Assert.assertEquals(0, lsync.getPullEpoch_());
        lsync.setPullEpoch_(42, t);
        Assert.assertEquals(42, lsync.getPullEpoch_());
    }

    @Test
    public void shouldUpdatePushEpoch() throws SQLException
    {
        Assert.assertEquals(0, lsync.getPushEpoch_());
        lsync.setPushEpoch_(42, t);
        Assert.assertEquals(42, lsync.getPushEpoch_());
    }

    private void addBootstrapSOIDs(SOID... soids) throws SQLException
    {
        SyncStatusDatabase.addBootstrapSOIDs(dbcw.getConnection(),
                Lists.newArrayList(soids));
    }

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

    @Test
    public void shouldListAndRemoveBootstrapSOIDs() throws SQLException
    {
        assertBootstrapSeq();
        addBootstrapSOIDs(o1, o2, o3);

        assertBootstrapSeq(o1, o2, o3);
        lsync.removeBootsrapSOID_(o1, t);
        assertBootstrapSeq(o2, o3);
        lsync.removeBootsrapSOID_(o2, t);
        assertBootstrapSeq(o3);
        lsync.removeBootsrapSOID_(o3, t);
        assertBootstrapSeq();
    }
}
