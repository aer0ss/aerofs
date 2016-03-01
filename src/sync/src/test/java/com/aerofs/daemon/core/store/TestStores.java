/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.acl.EffectiveUserList;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.SyncSchema;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.testlib.InMemorySQLiteDBCW;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestStores extends AbstractTest
{
    @Mock SIDMap sm;
    @Mock MapSIndex2Store sidx2s;
    @Mock Trans t;
    @Mock StoreDeletionOperators sdo;
    @Mock Store store;
    @Mock Store parentStore;
    @Mock Store extraStore;
    @Mock EffectiveUserList effectiveUserList;


    IStoreDatabase sdb;
    Stores ss;
    StoreHierarchy sh;

    SIndex sidx = new SIndex(1);
    SIndex sidxParent = new SIndex(2);
    SIndex sidxExtra = new SIndex(3);

    @Before
    public void setup() throws SQLException
    {
        InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
        dbcw.init_();
        try (Statement s = dbcw.getConnection().createStatement()) {
            new SyncSchema().create_(s, dbcw);
        }
        sdb = new StoreDatabase(dbcw);

        sh = new StoreHierarchy(sdb);
        Store.Factory factStore = mock(Store.Factory.class);
        ss = new Stores(sh, sm, factStore, sidx2s, sdo, effectiveUserList);

        when(sidx2s.get_(sidx)).thenReturn(store);
        when(factStore.create_(sidx)).thenReturn(store);
        when(factStore.create_(sidxParent)).thenReturn(parentStore);
        when(factStore.create_(sidxExtra)).thenReturn(extraStore);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnSettingParentsOnNonexisingStore() throws SQLException
    {
        sh.add_(sidxParent, "", t);
        sh.addParent_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnDeletingParentsOnNonexisingStore() throws SQLException
    {
        sh.add_(sidxParent, "", t);
        sh.deleteParent_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnSettingParentsOnNonexisingParent() throws SQLException
    {
        sh.add_(sidx, "", t);
        sh.addParent_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnDeletingParentsOnNonexisingParent() throws SQLException
    {
        sh.add_(sidx, "", t);
        sh.deleteParent_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnGettingParentsOnNonexisingStore() throws SQLException
    {
        sh.getParents_(sidx);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnGettingChildrenOnNonexisingStore() throws SQLException
    {
        sh.getChildren_(sidx);
    }

    @Test(expected = SQLException.class)
    public void shouldFailAddingExisingStore() throws SQLException
    {
        sh.add_(sidx, "", t);
        sh.add_(sidx, "", t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnDeletingNonexisingStore() throws SQLException
    {
        ss.deleteStore_(sidx, t);
    }

    @Test
    public void shouldDeleteStorePersistentDataAfterCleaningDevices() throws SQLException
    {
        sh.add_(sidx, "", t);

        ss.deleteStore_(sidx, t);

        InOrder inOrder = inOrder(store);
        inOrder.verify(store).preDelete_();
        inOrder.verify(store).deletePersistentData_(t);
    }

    @Test
    public void shouldReportCorrectParentSet() throws SQLException
    {
        sh.add_(sidx, "", t);
        sh.add_(sidxParent, "", t);
        sh.addParent_(sidx, sidxParent, t);

        assertEquals(ImmutableSet.of(sidxParent), sh.getParents_(sidx));
    }

    @Test
    public void shouldReportCorrectChildrenSet() throws SQLException
    {
        sh.add_(sidx, "", t);
        sh.add_(sidxParent, "", t);
        sh.addParent_(sidx, sidxParent, t);

        assertEquals(ImmutableSet.of(sidx), sh.getChildren_(sidxParent));
    }

    @Test
    public void shouldReportCorrectParentSetAfterInsert() throws SQLException
    {
        sh.add_(sidx, "", t);
        sh.add_(sidxParent, "", t);
        sh.add_(sidxExtra, "", t);
        sh.addParent_(sidx, sidxParent, t);

        assertEquals(ImmutableSet.of(sidxParent), sh.getParents_(sidx));

        sh.addParent_(sidx, sidxExtra, t);

        assertEquals(ImmutableSet.of(sidxParent, sidxExtra), sh.getParents_(sidx));
    }

    @Test
    public void shouldReportCorrectChildrenSetAfterInsert() throws SQLException
    {
        sh.add_(sidx, "", t);
        sh.add_(sidxParent, "", t);
        sh.add_(sidxExtra, "", t);
        sh.addParent_(sidx, sidxParent, t);

        assertEquals(ImmutableSet.of(sidx), sh.getChildren_(sidxParent));

        sh.addParent_(sidxExtra, sidxParent, t);

        assertEquals(ImmutableSet.of(sidx, sidxExtra), sh.getChildren_(sidxParent));
    }

    @Test
    public void shouldReportCorrectParentSetAfterRemove() throws SQLException
    {
        sh.add_(sidx, "", t);
        sh.add_(sidxParent, "", t);
        sh.addParent_(sidx, sidxParent, t);

        assertEquals(ImmutableSet.of(sidxParent), sh.getParents_(sidx));

        sh.deleteParent_(sidx, sidxParent, t);

        assertEquals(ImmutableSet.<SIndex>of(), sh.getParents_(sidx));
    }

    @Test
    public void shouldReportCorrectChildrenSetAfterRemove() throws SQLException
    {
        sh.add_(sidx, "", t);
        sh.add_(sidxParent, "", t);
        sh.addParent_(sidx, sidxParent, t);

        assertEquals(ImmutableSet.of(sidx), sh.getChildren_(sidxParent));

        sh.deleteParent_(sidx, sidxParent, t);

        assertEquals(ImmutableSet.<SIndex>of(), sh.getChildren_(sidxParent));
    }
}
