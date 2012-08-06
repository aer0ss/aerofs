package com.aerofs.lib.db;

import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.Collection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.aerofs.daemon.lib.db.IStoreDatabase.StoreRow;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.SIndex;
import com.aerofs.testlib.AbstractTest;

public class TestStoreDatabase extends AbstractTest
{
    InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
    StoreDatabase db = new StoreDatabase(dbcw.mockCoreDBCW());

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
        db.add_(sidx, sidxParent, t);
        db.add_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnDeletingNonexistingStore() throws ExAlreadyExist, SQLException
    {
        db.delete_(sidx, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionIfSettingParentOnNonexistingStore() throws SQLException
    {
        db.setParent_(sidx, sidxParent, t);
    }

    @Test
    public void shouldReturnParentAsSet() throws SQLException
    {
        db.add_(sidx, sidxParent, t);
        db.setParent_(sidx, sidxParent, t);
        Collection<StoreRow> srs = db.getAll_();
        assertEquals(srs.size(), 1);
        for (StoreRow sr : srs) assertTrue(sr._sidxParent.equals(sidxParent));
    }

    @Test
    public void shouldReturnExistingStoresOnly() throws SQLException
    {
        db.add_(sidx, sidxParent, t);
        db.add_(sidx2, sidxParent, t);
        db.delete_(sidx, t);

        Collection<StoreRow> srs = db.getAll_();
        assertEquals(srs.size(), 1);
        for (StoreRow sr : srs) assertTrue(sr._sidx.equals(sidx2));
    }

}
