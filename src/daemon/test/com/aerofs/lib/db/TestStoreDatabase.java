package com.aerofs.lib.db;

import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.Collection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.id.SIndex;
import com.aerofs.testlib.AbstractTest;

public class TestStoreDatabase extends AbstractTest
{
    InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
    StoreDatabase db = new StoreDatabase(dbcw.getCoreDBCW());

    @Mock Trans t;

    SIndex sidx = new SIndex(111);
    SIndex sidx2 = new SIndex(222);
    SIndex sidxParent = new SIndex(333);

    @Before
    public void setup() throws SQLException
    {
        dbcw.init_();
    }

    @After
    public void tearDown() throws Exception
    {
        dbcw.fini_();
    }

    @Test(expected = SQLException.class)
    public void shouldThrowOnAddingExistingStore() throws ExAlreadyExist, SQLException
    {
        db.insert_(sidx, "", false, t);
        db.insert_(sidx, "", false, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnDeletingNonexistingStore() throws ExAlreadyExist, SQLException
    {
        db.delete_(sidx, t);
    }

    @Test
    public void shouldReturnParents() throws SQLException
    {
        db.insert_(sidx, "", false, t);
        db.insertParent_(sidx, sidxParent, t);
        Collection<SIndex> sidxs = db.getParents_(sidx);
        assertEquals(sidxs.size(), 1);
        for (SIndex sidx : sidxs) assertTrue(sidx.equals(sidxParent));
    }

    @Test
    public void shouldReturnExistingStoresOnly() throws SQLException
    {
        db.insert_(sidx, "", false, t);
        db.insert_(sidx2, "", false, t);
        db.delete_(sidx, t);

        Collection<SIndex> sidxs = db.getAll_();
        assertEquals(sidxs.size(), 1);
        for (SIndex sidx : sidxs) assertTrue(sidx.equals(sidx2));
    }


    @Test
    public void collectingContent_shouldWorkAsExpected()
            throws SQLException
    {
        db.insert_(sidx, "", false, t);

        // by default content collection is enabled
        assertTrue(db.isCollectingContent_(sidx));

        db.setCollectingContent_(sidx, false, t);
        assertFalse(db.isCollectingContent_(sidx));
        db.setCollectingContent_(sidx, true, t);
        assertTrue(db.isCollectingContent_(sidx));
    }
}
