package com.aerofs.daemon.lib.db.ver;

import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.id.DID;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import com.aerofs.lib.Version;
import com.aerofs.lib.id.CID;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.testlib.AbstractTest;

import java.sql.SQLException;

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
    public void setup() throws SQLException
    {
        SIndex sidx = new SIndex(7);
        OID oid = new OID(UniqueID.generate());
        CID cid = new CID(1);
        socid = new SOCID(sidx, oid, cid);
        DID did = new DID(UniqueID.generate());
        Version versionNonZero = new Version();
        versionNonZero.set_(did, 3);
        // We pretend that some change left nonzero versions lying around even after store deletion
        when(nvdb.getAllVersions_(any(SOCID.class))).thenReturn(versionNonZero);
    }

    @Test
    public void shouldDeleteMaxTicksAndImmOnCommitWhenVersDeletedPermanently()
        throws Exception
    {
        va.versionDeletedPermanently_(socid, v);
        va.committing_(t);

        verify(nvdb).deleteMaxTicks_(socid, t);
        verify(ivdb).deleteImmigrantTicks_(socid, t);
    }

    @Test
    public void shouldNotAssertOnCommitWhenDifferentStoreDeletedAndVersDeletedPermanently()
        throws Exception
    {
        va.versionDeletedPermanently_(socid, v);
        deleteArbitrarySIndexAndCommit();
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertOnCommitWhenSameStoreDeletedAndVersionDeletedPermanently()
        throws Exception
    {
        va.versionDeletedPermanently_(socid, v);
        deleteSIndexAndCommit(socid.sidx());
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertOnCommitWhenSameStoreDeletedAndKMLAdded()
            throws Exception
    {
        va.kmlVersionAdded_(socid, v);
        deleteSIndexAndCommit(socid.sidx());
    }

    @Test
    public void shouldNotAssertOnCommitWhenDifferentStoreDeletedAndKMLAdded()
        throws Exception
    {
        va.kmlVersionAdded_(socid, v);
        deleteArbitrarySIndexAndCommit();
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertOnLocalVersionAddedAfterStoreDeleted()
        throws Exception
    {
        va.storeDeleted_(socid.sidx());
        va.localVersionAdded_(socid, v);
        va.committing_(t);
    }

    // FIXME This test requires more thorough mocking of VersionAssistant
    // dependencies and is beyond the scope of markj's test
    @Ignore@Test
    public void shouldNotAssertOnCommitWhenDifferentStoreDeletedAndLocalVersionAdded()
        throws Exception
    {
        va.localVersionAdded_(socid, v);
        deleteArbitrarySIndexAndCommit();
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertOnCommitWhenSameStoreDeletedAndVersionDeleted()
        throws Exception
    {
        va.storeDeleted_(socid.sidx());
        va.versionDeleted_(socid, v);
        va.committing_(t);
    }

    @Test
    public void shouldNotAssertOnCommitWhenDifferentStoreDeletedAndVersionDeleted()
        throws Exception
    {
        va.versionDeleted_(socid, v);
        deleteArbitrarySIndexAndCommit();
    }

    /**
     * This method is used when the caller doesn't care what SIndex is
     * deleted, only that it is not equal to socid.sidx()
     */
    private void deleteArbitrarySIndexAndCommit() throws Exception
    {
        SIndex sidx = new SIndex(5);
        assertFalse(sidx == socid.sidx());
        deleteSIndexAndCommit(sidx);
    }

    private void deleteSIndexAndCommit(SIndex sidx) throws Exception
    {
        va.storeDeleted_(sidx);
        va.committing_(t);
    }
}
