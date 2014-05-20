/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.net.device.DevicePresence;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.lib.id.SIndex;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

public class TestStores extends AbstractTest
{
    @Mock SIDMap sm;
    @Mock MapSIndex2Store sidx2s;
    @Mock DevicePresence dp;
    @Mock Trans t;
    @Mock StoreDeletionOperators sdo;
    @Mock Store store;
    @Mock Store parentStore;
    @Mock Store extraStore;

    IStoreDatabase sdb;
    Stores ss;

    SIndex sidx = new SIndex(1);
    SIndex sidxParent = new SIndex(2);
    SIndex sidxExtra = new SIndex(3);

    @Before
    public void setup() throws SQLException
    {
        InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
        dbcw.init_();
        sdb = new StoreDatabase(dbcw.getCoreDBCW());

        ss = new Stores();
        ss.inject_(sdb, sm, sidx2s, dp, sdo);

        when(sidx2s.get_(sidx)).thenReturn(store);
        when(sidx2s.add_(sidx)).thenReturn(store);
        when(sidx2s.add_(sidxParent)).thenReturn(parentStore);
        when(sidx2s.add_(sidxExtra)).thenReturn(extraStore);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnSettingParentsOnNonexisingStore() throws SQLException
    {
        ss.add_(sidxParent, "", t);
        ss.addParent_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnDeletingParentsOnNonexisingStore() throws SQLException
    {
        ss.add_(sidxParent, "", t);
        ss.deleteParent_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnSettingParentsOnNonexisingParent() throws SQLException
    {
        ss.add_(sidx, "", t);
        ss.addParent_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnDeletingParentsOnNonexisingParent() throws SQLException
    {
        ss.add_(sidx, "", t);
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
        ss.add_(sidx, "", t);
        ss.add_(sidx, "", t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnDeletingNonexisingStore() throws SQLException
    {
        ss.deleteStore_(sidx, t);
    }

    @Test
    public void shouldDeleteStorePersistentDataAfterCleaningDevicePresence() throws SQLException
    {
        ss.add_(sidx, "", t);

        ss.deleteStore_(sidx, t);

        InOrder inOrder = inOrder(dp, store);
        inOrder.verify(dp).beforeDeletingStore_(sidx);
        inOrder.verify(store).deletePersistentData_(t);
    }

    @Test
    public void shouldReportCorrectParentSet() throws SQLException
    {
        ss.add_(sidx, "", t);
        ss.add_(sidxParent, "", t);
        ss.addParent_(sidx, sidxParent, t);

        assertEquals(ImmutableSet.of(sidxParent), ss.getParents_(sidx));
    }

    @Test
    public void shouldReportCorrectChildrenSet() throws SQLException
    {
        ss.add_(sidx, "", t);
        ss.add_(sidxParent, "", t);
        ss.addParent_(sidx, sidxParent, t);

        assertEquals(ImmutableSet.of(sidx), ss.getChildren_(sidxParent));
    }

    @Test
    public void shouldReportCorrectParentSetAfterInsert() throws SQLException
    {
        ss.add_(sidx, "", t);
        ss.add_(sidxParent, "", t);
        ss.add_(sidxExtra, "", t);
        ss.addParent_(sidx, sidxParent, t);

        assertEquals(ImmutableSet.of(sidxParent), ss.getParents_(sidx));

        ss.addParent_(sidx, sidxExtra, t);

        assertEquals(ImmutableSet.of(sidxParent, sidxExtra), ss.getParents_(sidx));
    }

    @Test
    public void shouldReportCorrectChildrenSetAfterInsert() throws SQLException
    {
        ss.add_(sidx, "", t);
        ss.add_(sidxParent, "", t);
        ss.add_(sidxExtra, "", t);
        ss.addParent_(sidx, sidxParent, t);

        assertEquals(ImmutableSet.of(sidx), ss.getChildren_(sidxParent));

        ss.addParent_(sidxExtra, sidxParent, t);

        assertEquals(ImmutableSet.of(sidx, sidxExtra), ss.getChildren_(sidxParent));
    }

    @Test
    public void shouldReportCorrectParentSetAfterRemove() throws SQLException
    {
        ss.add_(sidx, "", t);
        ss.add_(sidxParent, "", t);
        ss.addParent_(sidx, sidxParent, t);

        assertEquals(ImmutableSet.of(sidxParent), ss.getParents_(sidx));

        ss.deleteParent_(sidx, sidxParent, t);

        assertEquals(ImmutableSet.<SIndex>of(), ss.getParents_(sidx));
    }

    @Test
    public void shouldReportCorrectChildrenSetAfterRemove() throws SQLException
    {
        ss.add_(sidx, "", t);
        ss.add_(sidxParent, "", t);
        ss.addParent_(sidx, sidxParent, t);

        assertEquals(ImmutableSet.of(sidx), ss.getChildren_(sidxParent));

        ss.deleteParent_(sidx, sidxParent, t);

        assertEquals(ImmutableSet.<SIndex>of(), ss.getChildren_(sidxParent));
    }
}
