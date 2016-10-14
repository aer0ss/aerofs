package com.aerofs.lib.db;

import java.sql.SQLException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.*;

import com.aerofs.daemon.lib.db.SIDDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.ids.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.testlib.AbstractTest;

public class TestSIDDatabase extends AbstractTest
{
    InMemoryCoreDBCW dbcw = new InMemoryCoreDBCW();
    SIDDatabase db = new SIDDatabase(dbcw);

    @Mock Trans t;

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
    public void shouldThrowOnAddingExistingSID() throws ExAlreadyExist, SQLException
    {
        SID sid = SID.generate();
        db.insertSID_(sid, t);
        db.insertSID_(sid, t);
    }

    @Test
    public void shouldReturnTheSameSIndexAsWhenAdded() throws ExAlreadyExist, SQLException
    {
        SID sid = SID.generate();
        SIndex sidx = db.insertSID_(sid, t);
        assertEquals(sidx, db.getSIndex_(sid));
    }

    @Test
    public void shouldConvertBidirectionally() throws ExAlreadyExist, SQLException
    {
        SID sid = SID.generate();
        SIndex sidx = db.insertSID_(sid, t);
        assertEquals(sid, db.getSID_(sidx));
    }
}
