package com.aerofs.daemon.core.syncstatus;

import static com.aerofs.lib.db.CoreSchema.C_SSBS_OID;
import static com.aerofs.lib.db.CoreSchema.C_SSBS_SIDX;
import static com.aerofs.lib.db.CoreSchema.T_SSBS;

import java.sql.PreparedStatement;
import java.sql.SQLException;

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
import com.aerofs.daemon.core.store.Stores;
import com.aerofs.daemon.core.store.Stores.IDIDBiMap;
import com.aerofs.daemon.core.syncstatus.LocalSyncStatus;
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

public class TestLocalSyncStatus extends AbstractTest
{
    @Mock Trans t;
    @Mock TransManager tm;
    @Mock StoreCreator sc;
    @Mock SIDMap sm;
    @Mock MapSIndex2Store sidx2s;
    @Mock DevicePresence dp;

    InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
    IMetaDatabase mdb = new MetaDatabase(dbcw.mockCoreDBCW());
    IStoreDatabase sdb = new StoreDatabase(dbcw.mockCoreDBCW());
    ISyncStatusDatabase db = new SyncStatusDatabase(dbcw.mockCoreDBCW());

    Stores makeStores() {
        Stores s = new Stores();
        s.inject_(sdb, tm, sc, sm, sidx2s, dp);
        return s;
    }

    Stores stores = makeStores();

    LocalSyncStatus lsync = new LocalSyncStatus(dp, mdb, db, stores);

    final SIndex sidx = new SIndex(1);
    final DID d0 = new DID(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
    final DID d1 = new DID(new byte[] { 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
    final DID d2 = new DID(new byte[] { 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});

    final OID o1 = new OID(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
    final OID o2 = new OID(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2});
    final OID o3 = new OID(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3});

    @Before
    public void setup() throws Exception
    {
        dbcw.init_();
    }

    @After
    public void tearDown() throws Exception
    {
        dbcw.fini_();
    }

    private void assertDeviceList(DID... expected) throws SQLException {
        IDIDBiMap l = lsync.getDeviceMapping_(sidx);
        for (int i = 0; i < expected.length; ++i) {
            Assert.assertTrue(i < l.size());
            Assert.assertEquals(expected[i], l.get(i));
            Assert.assertEquals(i, l.get(expected[i]).intValue());
        }
        Assert.assertEquals(expected.length, l.size());
    }

    @Test
    public void shouldAddDevices() throws SQLException
    {
        sdb.add_(sidx, sidx, t);

        assertDeviceList();

        lsync.addDevice_(sidx, d0, t);
        assertDeviceList(d0);

        lsync.addDevice_(sidx, d0, t);
        assertDeviceList(d0);

        lsync.addDevice_(sidx, d1, t);
        assertDeviceList(d0, d1);

        lsync.addDevice_(sidx, d2, t);
        assertDeviceList(d0, d1, d2);
    }

    @Test
    public void shouldUpdateSyncStatus() throws SQLException, ExAlreadyExist
    {
        sdb.add_(sidx, sidx, t);
        mdb.createOA_(sidx, OID.ROOT, OID.ROOT, "R", OA.Type.DIR, 0, t);
        mdb.createOA_(sidx, o1, OID.ROOT, "foo", OA.Type.FILE, 0, t);
        mdb.createOA_(sidx, o2, OID.ROOT, "bar", OA.Type.DIR, 0, t);
        mdb.createOA_(sidx, o3, o2, "bar", OA.Type.FILE, 0, t);

        Assert.assertEquals(0, lsync.getSyncStatus_(new SOID(sidx, o1)).size());
        Assert.assertEquals(0, lsync.getSyncStatus_(new SOID(sidx, o2)).size());
        Assert.assertEquals(0, lsync.getSyncStatus_(new SOID(sidx, o3)).size());

        Assert.assertEquals(new BitVector(), lsync.getSyncStatus_(new SOID(sidx, o1)));
        Assert.assertEquals(new BitVector(), lsync.getSyncStatus_(new SOID(sidx, o2)));
        Assert.assertEquals(new BitVector(), lsync.getSyncStatus_(new SOID(sidx, o3)));

        lsync.addDevice_(sidx, d0, t);
        lsync.addDevice_(sidx, d1, t);
        lsync.addDevice_(sidx, d2, t);

        Assert.assertEquals(0, lsync.getSyncStatus_(new SOID(sidx, o1)).size());
        Assert.assertEquals(0, lsync.getSyncStatus_(new SOID(sidx, o2)).size());
        Assert.assertEquals(0, lsync.getSyncStatus_(new SOID(sidx, o3)).size());

        Assert.assertEquals(new BitVector(), lsync.getSyncStatus_(new SOID(sidx, o1)));
        Assert.assertEquals(new BitVector(), lsync.getSyncStatus_(new SOID(sidx, o2)));
        Assert.assertEquals(new BitVector(), lsync.getSyncStatus_(new SOID(sidx, o3)));

        lsync.setSyncStatus_(new SOID(sidx, o1), new BitVector(3, true), t);

        Assert.assertEquals(new BitVector(3, true), lsync.getSyncStatus_(new SOID(sidx, o1)));
        Assert.assertEquals(new BitVector(3, false), lsync.getSyncStatus_(new SOID(sidx, o2)));
        Assert.assertEquals(new BitVector(), lsync.getSyncStatus_(new SOID(sidx, o3)));
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

    private void assertBootstrapSeq(OID... expected) throws SQLException
    {
        IDBIterator<SOID> it = lsync.getBootstrapSOIDs_();
        try {
            for (OID oid : expected) {
                Assert.assertTrue(it.next_());
                Assert.assertEquals(new SOID(sidx, oid), it.get_());
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

        // CHEAT: bootstrap table filled by post update task, no API for that...
        PreparedStatement ps = dbcw.getConnection().prepareStatement(
                                    "insert into " + T_SSBS +
                                    " (" + C_SSBS_SIDX + "," + C_SSBS_OID + ")" +
                                    " values(?,?)");
        ps.setInt(1, sidx.getInt());
        ps.setBytes(2, o1.getBytes());
        ps.executeUpdate();
        ps.setBytes(2, o2.getBytes());
        ps.executeUpdate();
        ps.setBytes(2, o3.getBytes());
        ps.executeUpdate();

        assertBootstrapSeq(o1, o2, o3);
        lsync.removeBootsrapSOID_(new SOID(sidx, o1), t);
        assertBootstrapSeq(o2, o3);
        lsync.removeBootsrapSOID_(new SOID(sidx, o2), t);
        assertBootstrapSeq(o3);
        lsync.removeBootsrapSOID_(new SOID(sidx, o3), t);
        assertBootstrapSeq();
    }
}
