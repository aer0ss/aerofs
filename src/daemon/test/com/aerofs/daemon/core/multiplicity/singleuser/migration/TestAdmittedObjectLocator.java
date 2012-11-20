package com.aerofs.daemon.core.multiplicity.singleuser.migration;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.lib.cfg.CfgBuildType;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestAdmittedObjectLocator extends AbstractTest
{
    @Mock IMetaDatabase mdb;
    @Mock DirectoryService ds;
    @Mock CfgBuildType cfgBuildType;
    @Mock OA oaAdmitted;
    @Mock OA oaExpelled1;
    @Mock OA oaExpelled2;

    OA.Type type = OA.Type.DIR;
    OID oid = new OID(UniqueID.generate());
    SIndex sidxAdmitted = new SIndex(1);
    SIndex sidxExpelled1 = new SIndex(2);
    SIndex sidxExpelled2 = new SIndex(3);
    SOID soidAdmitted = new SOID(sidxAdmitted, oid);
    SOID soidExpelled1 = new SOID(sidxExpelled1, oid);
    SOID soidExpelled2 = new SOID(sidxExpelled2, oid);

    @InjectMocks AdmittedObjectLocator aol;

    @Before
    public void setup() throws ExNotFound, SQLException, ExNotDir
    {
        mockOA(oaAdmitted, soidAdmitted, false);
        mockOA(oaExpelled1, soidExpelled1, true);
        mockOA(oaExpelled2, soidExpelled2, true);

        when(mdb.getSIndexes_(oid, sidxAdmitted))
                .thenReturn(newSet(sidxExpelled1, sidxExpelled2));
        when(mdb.getSIndexes_(oid, sidxExpelled1))
                .thenReturn(newSet(sidxExpelled2, sidxAdmitted));
    }

    private void mockOA(OA oa, SOID soid, boolean expelled) throws SQLException
    {
        when(oa.type()).thenReturn(type);
        when(oa.soid()).thenReturn(soid);
        when(oa.isExpelled()).thenReturn(expelled);
        when(ds.getOA_(soid)).thenReturn(oa);
    }

    private static Set<SIndex> newSet(SIndex ... sidxs)
    {
        Set<SIndex> ret = new HashSet<SIndex>(sidxs.length);
        for (SIndex sidx : sidxs) ret.add(sidx);
        return ret;
    }

    ////////
    // enforcement tests

    @Test (expected = AssertionError.class)
    public void shouldAssertNoMoreThanOneAdmittedObject() throws SQLException
    {
        setupDoubleAdmittedObjects();
        aol.locate_(oid, sidxExpelled1, type);
    }

    private void setupDoubleAdmittedObjects() throws SQLException
    {
        when(cfgBuildType.isStaging()).thenReturn(true);

        OA oaAdmitted2 = mock(OA.class);
        SIndex sidxAdmitted2 = new SIndex(99);
        SOID soidAdmitted2 = new SOID(sidxAdmitted2, oid);
        mockOA(oaAdmitted2, soidAdmitted2, false);

        when(mdb.getSIndexes_(oid, sidxExpelled1))
                .thenReturn(newSet(sidxAdmitted, sidxAdmitted2, sidxExpelled1, sidxExpelled2));
    }

    @Test (expected = AssertionError.class)
    public void shouldAssertAllObjectsMatchExpectedType1() throws SQLException
    {
        aol.locate_(oid, sidxExpelled1, OA.Type.FILE);
    }

    @Test (expected = AssertionError.class)
    public void shouldAssertAllObjectsMatchExpectedType2() throws SQLException
    {
        aol.locate_(oid, sidxExpelled1, OA.Type.FILE);
    }

    ////////
    // logic tests

    @Test
    public void shouldReturnNoExpelledObjects() throws SQLException
    {
        assertNull(aol.locate_(oid, sidxAdmitted, type));

        when(mdb.getSIndexes_(oid, sidxAdmitted)).thenReturn(newSet(sidxExpelled1, sidxExpelled2));
        assertNull(aol.locate_(oid, sidxAdmitted, type));
    }

    @Test
    public void shouldReturnAdmittedObjects() throws SQLException
    {
        assertEquals(aol.locate_(oid, sidxExpelled1, type), oaAdmitted);
    }
}
