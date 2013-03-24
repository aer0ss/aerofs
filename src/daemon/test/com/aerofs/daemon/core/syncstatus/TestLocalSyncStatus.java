package com.aerofs.daemon.core.syncstatus;

import java.sql.SQLException;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryServiceImpl;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.store.DescendantStores;
import com.aerofs.daemon.core.store.DeviceBitMap;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserPathResolver;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserStores;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.base.id.UniqueID;
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
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.id.OID;
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
    @Mock AggregateSyncStatus assc;
    @Mock FrequentDefectSender fds;
    @Mock StoreDeletionOperators sdo;
    @Mock SingleuserStores sss;

    InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
    IMetaDatabase mdb = new MetaDatabase(dbcw.getCoreDBCW());
    IStoreDatabase sdb = new StoreDatabase(dbcw.getCoreDBCW());
    ISyncStatusDatabase ssdb = new SyncStatusDatabase(dbcw.getCoreDBCW());
    MapSIndex2DeviceBitMap sidx2dbm = new MapSIndex2DeviceBitMap(sdb);

    LocalSyncStatus lsync;

    final SID sid = SID.generate();
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

        when(sm.get_(sidx)).thenReturn(sid);

        DirectoryServiceImpl ds = new DirectoryServiceImpl();
        SingleuserPathResolver pathResolver = new SingleuserPathResolver(sss, ds, sm, sm);
        ds.inject_(ps, mdb, alias2target, tm, sm, fds, sdo, pathResolver);
        lsync = new LocalSyncStatus(ds, ssdb, sidx2dbm, assc, sdo, dss);
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
        sdb.insert_(sidx, t);

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
        sdb.insert_(sidx, t);
        mdb.insertOA_(sidx, OID.ROOT, OID.ROOT, "R", OA.Type.DIR, 0, t);
        mdb.insertOA_(sidx, o1.oid(), OID.ROOT, "foo", OA.Type.FILE, 0, t);
        mdb.insertOA_(sidx, o2.oid(), OID.ROOT, "bar", OA.Type.DIR, 0, t);
        mdb.insertOA_(sidx, o3.oid(), o2.oid(), "bar", OA.Type.FILE, 0, t);

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
        ssdb.setPullEpoch_(42, t);
        Assert.assertEquals(42, lsync.getPullEpoch_());
    }

    @Test
    public void shouldUpdatePushEpoch() throws SQLException
    {
        Assert.assertEquals(0, lsync.getPushEpoch_());
        ssdb.setPushEpoch_(42, t);
        Assert.assertEquals(42, lsync.getPushEpoch_());
    }
}
