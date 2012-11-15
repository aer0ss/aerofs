package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.store.IStoreDeletionListener.StoreDeletionNotifier;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.SIndex;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.io.IOException;
import java.sql.SQLException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

public class TestStores extends AbstractTest
{
    @Mock TransManager tm;
    @Mock StoreCreator sc;
    @Mock SIDMap sm;
    @Mock MapSIndex2Store sidx2s;
    @Mock DevicePresence dp;
    @Mock MapSIndex2DeviceBitMap sidx2dbm;
    @Mock StoreDeletionNotifier sdn;
    @Mock IStoreDatabase sdb;

    @Mock Store store;
    @Mock Trans t;

    @InjectMocks Stores ss;

    SIndex sidx = new SIndex(1);
    SIndex sidxParent = new SIndex(2);

    @Before
    public void setup() throws SQLException
    {
        when(sidx2s.get_(sidx)).thenReturn(store);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnAddingExisingStore() throws SQLException
    {
        ss.add_(sidx, sidxParent, t);
        ss.add_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnDeletingNonexisingStore() throws SQLException
    {
        ss.onStoreDeletion_(sidx, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnSettingParentOnNonexisingStore() throws SQLException
    {
        ss.setParent_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnGettingParentOnNonexisingStore() throws SQLException
    {
        ss.getParent_(sidx);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnGettingChildrenOnNonexisingStore() throws SQLException
    {
        ss.getChildren_(sidx);
    }

    @Test
    public void shouldDeleteStorePersistentDataAfterCleaningDevicePresence() throws SQLException
    {
        // Load the map with a store
        ss.add_(sidx, sidxParent, t);

        ss.onStoreDeletion_(sidx, t);

        InOrder inOrder = inOrder(dp, store);
        inOrder.verify(dp).beforeDeletingStore_(sidx);
        inOrder.verify(store).deletePersistentData_(t);
    }

    /**
     * isRoot(sidx) should return true if and only if getParent(sidx) returns sidx.
     */
    @Test
    public void shouldMaintainConsistencyBetweenIsRootAndGetParent()
            throws SQLException, IOException, ExAlreadyExist
    {
        ss.add_(sidx, sidxParent, t);
        ss.add_(sidxParent, sidxParent, t);

        assertFalse(ss.getParent_(sidx).equals(sidx));
        assertFalse(ss.isRoot_(sidx));

        assertTrue(ss.getParent_(sidxParent).equals(sidxParent));
        assertTrue(ss.isRoot_(sidxParent));
    }

}
