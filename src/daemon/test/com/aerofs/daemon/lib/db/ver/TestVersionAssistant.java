package com.aerofs.daemon.lib.db.ver;

import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.lib.db.trans.Trans;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import com.aerofs.lib.Version;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.testlib.AbstractTest;

public class TestVersionAssistant extends AbstractTest
{
    @Mock INativeVersionDatabase nvdb;
    @Mock IImmigrantVersionDatabase ivdb;
    @Mock MapSIndex2Store _sidx2s;

    @InjectMocks VersionAssistant va;

    @Mock Trans t;
    @Mock Version v;
    SOCID socid;

    @Before
    public void setup()
    {
        SIndex sidx = new SIndex(7);
        OID oid = new OID(UniqueID.generate());
        CID cid = new CID(1);
        socid = new SOCID(sidx, oid, cid);
    }

    @Test
    public void shouldDeleteMaxTicksAndImmOnFlushWhenVersDeletedPermanently()
        throws Exception
    {
        va.versionDeletedPermanently_(socid, v);
        va.committing_(t);

        verify(nvdb).deleteMaxTicks_(socid, t);
        verify(ivdb).deleteImmigrantTicks_(socid, t);
    }

    @Test
    public void shouldNotAssertOnFlushWhenDifferentStoreDeletedAndVersDeletedPermanently()
        throws Exception
    {
        va.versionDeletedPermanently_(socid, v);
        deleteArbitrarySIndexAndFlush();
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertOnFlushWhenSameStoreDeletedAndVersionDeletedPermanently()
        throws Exception
    {
        va.versionDeletedPermanently_(socid, v);
        deleteSIndexAndFlush(socid.sidx());
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertOnFlushWhenSameStoreDeletedAndKMLAdded() throws Exception
    {
        va.kmlVersionAdded_(socid, v);
        deleteSIndexAndFlush(socid.sidx());
    }

    @Test
    public void shouldNotAssertOnFlushWhenDifferentStoreDeletedAndKMLAdded()
        throws Exception
    {
        va.kmlVersionAdded_(socid, v);
        deleteArbitrarySIndexAndFlush();
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertOnFlushWhenSameStoreDeletedAndLocalVersionAdded()
        throws Exception
    {
        va.localVersionAdded_(socid, v);
        deleteSIndexAndFlush(socid.sidx());
    }

    // FIXME This test requires more thorough mocking of VersionAssistant
    // dependencies and is beyond the scope of markj's test
    @Ignore@Test
    public void shouldNotAssertOnFlushWhenDifferentStoreDeletedAndLocalVersionAdded()
        throws Exception
    {
        va.localVersionAdded_(socid, v);
        deleteArbitrarySIndexAndFlush();
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertOnFlushWhenSameStoreDeletedAndVersionDeleted()
        throws Exception
    {
        va.versionDeleted_(socid, v);
        deleteSIndexAndFlush(socid.sidx());
    }

    @Test
    public void shouldNotAssertOnFlushWhenDifferentStoreDeletedAndVersionDeleted()
        throws Exception
    {
        va.versionDeleted_(socid, v);
        deleteArbitrarySIndexAndFlush();
    }

    /**
     * This method is used when the caller doesn't care what SIndex is
     * deleted, only that it is not equal to socid.sidx()
     */
    private void deleteArbitrarySIndexAndFlush() throws Exception
    {
        SIndex sidx = new SIndex(5);
        assertFalse(sidx == socid.sidx());
        deleteSIndexAndFlush(sidx);
    }

    private void deleteSIndexAndFlush(SIndex sidx) throws Exception
    {
        va.storeDeleted_(sidx);
        va.committing_(t);
    }
}
