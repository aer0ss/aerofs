/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.lib.id.SIndex;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.sql.SQLException;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

public class TestStores extends AbstractTest
{
    @Mock SIDMap sm;
    @Mock MapSIndex2Store sidx2s;
    @Mock DevicePresence dp;
    @Mock MapSIndex2DeviceBitMap sidx2dbm;
    @Mock Trans t;
    @Mock StoreDeletionOperators sdo;
    @Mock Store store;

    IStoreDatabase sdb;
    Stores ss;

    SIndex sidx = new SIndex(1);
    SIndex sidxParent = new SIndex(2);

    @Before
    public void setup() throws SQLException
    {
        InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
        dbcw.init_();
        sdb = new StoreDatabase(dbcw.getCoreDBCW());

        ss = new Stores();
        ss.inject_(sdb, sm, sidx2s, sidx2dbm, dp, sdo);

        when(sidx2s.get_(sidx)).thenReturn(store);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnSettingParentsOnNonexisingStore() throws SQLException
    {
        ss.add_(sidxParent, t);
        ss.addParent_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnDeletingParentsOnNonexisingStore() throws SQLException
    {
        ss.add_(sidxParent, t);
        ss.deleteParent_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnSettingParentsOnNonexisingParent() throws SQLException
    {
        ss.add_(sidx, t);
        ss.addParent_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnDeletingParentsOnNonexisingParent() throws SQLException
    {
        ss.add_(sidx, t);
        ss.deleteParent_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnGettingParentsOnNonexisingStore() throws SQLException
    {
        ss.getParents_(sidx);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnGettingChildrenOnNonexisingStore() throws SQLException
    {
        ss.getChildren_(sidx);
    }

    @Test(expected = SQLException.class)
    public void shouldFailAddingExisingStore() throws SQLException
    {
        ss.add_(sidx, t);
        ss.add_(sidx, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnDeletingNonexisingStore() throws SQLException
    {
        ss.deleteStore_(sidx, t);
    }

    @Test
    public void shouldDeleteStorePersistentDataAfterCleaningDevicePresence() throws SQLException
    {
        ss.add_(sidx, t);

        ss.deleteStore_(sidx, t);

        InOrder inOrder = inOrder(dp, store);
        inOrder.verify(dp).beforeDeletingStore_(sidx);
        inOrder.verify(store).deletePersistentData_(t);
    }
}
