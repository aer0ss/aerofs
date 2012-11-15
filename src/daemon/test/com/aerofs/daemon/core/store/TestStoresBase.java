package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.store.IStoreDeletionListener.StoreDeletionNotifier;
import com.aerofs.daemon.core.store.StoresBase.IStoresBaseOperator;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.id.SIndex;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.sql.SQLException;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

public class TestStoresBase extends AbstractTest
{
    @Mock TransManager tm;
    @Mock StoreCreator sc;
    @Mock SIDMap sm;
    @Mock MapSIndex2Store sidx2s;
    @Mock DevicePresence dp;
    @Mock MapSIndex2DeviceBitMap sidx2dbm;
    @Mock StoreDeletionNotifier sdn;
    @Mock IStoreDatabase sdb;
    @Mock IStoresBaseOperator operator;

    @Mock Store store;
    @Mock Trans t;

    @InjectMocks StoresBase ssb;

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
        ssb.add_(sidx, sidxParent, t);
        ssb.add_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnDeletingNonexisingStore() throws SQLException
    {
        ssb.onStoreDeletion_(sidx, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnSettingParentOnNonexisingStore() throws SQLException
    {
        ssb.setParent_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnGettingParentOnNonexisingStore() throws SQLException
    {
        ssb.getParent_(sidx);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnGettingChildrenOnNonexisingStore() throws SQLException
    {
        ssb.getChildren_(sidx);
    }

    @Test
    public void shouldDeleteStorePersistentDataAfterCleaningDevicePresence() throws SQLException
    {
        // Load the map with a store
        ssb.add_(sidx, sidxParent, t);

        ssb.onStoreDeletion_(sidx, t);

        InOrder inOrder = inOrder(dp, store);
        inOrder.verify(dp).beforeDeletingStore_(sidx);
        inOrder.verify(store).deletePersistentData_(t);
    }
}
