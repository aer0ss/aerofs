package com.aerofs.lib.db;

import java.sql.SQLException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.*;

import com.aerofs.daemon.lib.db.SIDDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.testlib.AbstractTest;

public class TestSIDDatabase extends AbstractTest
{
    InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
    SIDDatabase db = new SIDDatabase(dbcw.mockCoreDBCW());

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
        SID sid = new SID(UniqueID.generate());
        db.addSID_(sid, t);
        db.addSID_(sid, t);
    }

    public void shouldReturnTheSameSIndexAsWhenAdded() throws ExAlreadyExist, SQLException
    {
        SID sid = new SID(UniqueID.generate());
        SIndex sidx = db.addSID_(sid, t);
        assertEquals(sidx, db.getSIndex_(sid));
    }

    public void shouldConvertBidirectionally() throws ExAlreadyExist, SQLException
    {
        SID sid = new SID(UniqueID.generate());
        SIndex sidx = db.addSID_(sid, t);
        assertEquals(sid, db.getSID_(sidx));
    }
}
