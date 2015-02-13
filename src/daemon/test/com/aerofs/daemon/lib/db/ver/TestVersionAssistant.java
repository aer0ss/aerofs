package com.aerofs.daemon.lib.db.ver;

import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.DID;
import com.aerofs.lib.Tick;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import com.aerofs.lib.Version;
import com.aerofs.lib.id.CID;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.ids.UniqueID;
import com.aerofs.testlib.AbstractTest;

import java.sql.SQLException;

public class TestVersionAssistant extends AbstractTest
{
    @Mock INativeVersionDatabase nvdb;
    @Mock IImmigrantVersionDatabase ivdb;
    @Mock MapSIndex2Store _sidx2s;

    @InjectMocks VersionAssistant.Factory factVA;

    @Mock Trans t;
    @Mock Version v;
    SOCID socid;

    VersionAssistant va;

    @Before
    public void setup() throws SQLException
    {
        SIndex sidx = new SIndex(7);
        OID oid = new OID(UniqueID.generate());
        CID cid = new CID(1);
        socid = new SOCID(sidx, oid, cid);
        Version versionNonZero = Version.of(DID.generate(), new Tick(3));
        // We pretend that some change left nonzero versions lying around even after store deletion
        when(nvdb.getAllVersions_(any(SOCID.class))).thenReturn(versionNonZero);

        va = factVA.create_();
    }

    @Test
    public void shouldDeleteMaxTicksAndImmOnCommitWhenVersDeletedPermanently()
        throws Exception
    {
        va.versionDeletedPermanently_(socid);
        va.committing_(t);

        verify(nvdb).deleteMaxTicks_(socid, t);
        verify(ivdb).deleteImmigrantTicks_(socid, t);
    }

    @Test
    public void shouldNotAssertOnCommitWhenDifferentStoreDeletedAndVersDeletedPermanently()
        throws Exception
    {
        va.versionDeletedPermanently_(socid);
        deleteArbitrarySIndexAndCommit();
    }

    @Test
    public void shouldNotAssertOnCommitWhenDifferentStoreDeletedAndKMLAdded()
        throws Exception
    {
        va.kmlVersionAdded_(socid);
        deleteArbitrarySIndexAndCommit();
    }

    // FIXME This test requires more thorough mocking of VersionAssistant
    // dependencies and is beyond the scope of markj's test
    @Ignore@Test
    public void shouldNotAssertOnCommitWhenDifferentStoreDeletedAndLocalVersionAdded()
        throws Exception
    {
        va.localVersionAdded_(socid);
        deleteArbitrarySIndexAndCommit();
    }

    @Test
    public void shouldNotAssertOnCommitWhenDifferentStoreDeletedAndVersionDeleted()
        throws Exception
    {
        va.versionDeleted_(socid);
        deleteArbitrarySIndexAndCommit();
    }

    /**
     * This method is used when the caller doesn't care what SIndex is
     * deleted, only that it is not equal to socid.sidx()
     */
    private void deleteArbitrarySIndexAndCommit() throws Exception
    {
        SIndex sidx = new SIndex(5);
        assertFalse(sidx.equals(socid.sidx()));
        deleteSIndexAndCommit(sidx);
    }

    private void deleteSIndexAndCommit(SIndex sidx) throws Exception
    {
        va.committing_(t);
    }
}
