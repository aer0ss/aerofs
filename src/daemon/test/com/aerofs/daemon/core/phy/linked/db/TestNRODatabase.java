package com.aerofs.daemon.core.phy.linked.db;

import com.aerofs.ids.OID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.InMemoryCoreDBCW;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.testlib.AbstractTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestNRODatabase extends AbstractTest
{
    @Mock Trans t;

    IDBCW dbcw = new InMemoryCoreDBCW();

    NRODatabase nrodb;

    @Before
    public void setUp() throws SQLException
    {
        dbcw.init_();
        try (Statement s = dbcw.getConnection().createStatement()) {
            new LinkedStorageSchema().create_(s, dbcw);
        }
        nrodb = new NRODatabase(dbcw);
    }

    @After
    public void tearDown() throws SQLException
    {
        dbcw.fini_();
    }

    static SOID newSOID()
    {
        return newSOID(new SIndex(new Random().nextInt()));
    }

    static SOID newSOID(SIndex sidx)
    {
        return new SOID(sidx, OID.generate());
    }

    @Test
    public void shouldBeRepresentableByDefault() throws Exception
    {
        assertFalse(nrodb.isNonRepresentable_(newSOID()));
    }

    @Test
    public void shouldMarkInherentlyNonRepresentable() throws Exception
    {
        SOID soid = newSOID();
        nrodb.setNonRepresentable_(soid, null, t);
        assertTrue(nrodb.isNonRepresentable_(soid));
    }

    @Test
    public void shouldMarkRepresentable() throws Exception
    {
        SOID soid = newSOID();
        nrodb.setNonRepresentable_(soid, null, t);
        nrodb.setRepresentable_(soid, t);
        assertFalse(nrodb.isNonRepresentable_(soid));
    }

    @Test
    public void shouldMarkContextuallyNonRepresentable() throws Exception
    {
        SOID soid = newSOID();
        SOID conflict = newSOID(soid.sidx());
        nrodb.setNonRepresentable_(soid, conflict, t);
        assertTrue(nrodb.isNonRepresentable_(soid));
        assertFalse(nrodb.isNonRepresentable_(conflict));

        IDBIterator<SOID> it = nrodb.getConflicts_(conflict);
        assertTrue(it.next_());
        assertEquals(soid, it.get_());
        assertFalse(it.next_());

        it = nrodb.getConflicts_(soid);
        assertFalse(it.next_());
    }

    @Test
    public void shouldMarkContextuallyRepresentable() throws Exception
    {
        SOID soid = newSOID();
        SOID conflict = newSOID(soid.sidx());
        nrodb.setNonRepresentable_(soid, conflict, t);
        nrodb.setRepresentable_(soid, t);
        assertFalse(nrodb.isNonRepresentable_(soid));
        assertFalse(nrodb.isNonRepresentable_(conflict));

        IDBIterator<SOID> it = nrodb.getConflicts_(soid);
        assertFalse(it.next_());

        it = nrodb.getConflicts_(conflict);
        assertFalse(it.next_());
    }

    @Test
    public void shouldUpdateConflicts() throws Exception
    {
        SOID o0 = newSOID();
        SOID o1 = newSOID(o0.sidx());
        SOID o2 = newSOID(o0.sidx());
        nrodb.setNonRepresentable_(o1, o0, t);
        nrodb.setNonRepresentable_(o2, o0, t);

        IDBIterator<SOID> it = nrodb.getConflicts_(o0);
        assertTrue(it.next_());
        assertTrue(it.next_());
        assertFalse(it.next_());

        nrodb.setRepresentable_(o1, t);
        nrodb.updateConflicts_(o0, o1.oid(), t);

        it = nrodb.getConflicts_(o0);
        assertFalse(it.next_());

        it = nrodb.getConflicts_(o1);
        assertTrue(it.next_());
        assertEquals(o2, it.get_());
        assertFalse(it.next_());

        it = nrodb.getConflicts_(o2);
        assertFalse(it.next_());
    }
}
