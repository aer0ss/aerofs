package com.aerofs.daemon.core.multiplicity.singleuser.migration;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.lib.cfg.CfgAggressiveChecking;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.ids.UniqueID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestAdmittedObjectLocator extends AbstractTest
{
    @Mock StoreHierarchy stores;
    @Mock DirectoryService ds;
    @Mock CfgAggressiveChecking cfgAggressiveChecking;
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

        when(stores.getAll_())
                .thenReturn(ImmutableSet.of(sidxAdmitted, sidxExpelled1, sidxExpelled2));
    }

    private void mockOA(OA oa, SOID soid, boolean expelled) throws SQLException
    {
        when(oa.type()).thenReturn(type);
        when(oa.soid()).thenReturn(soid);
        when(oa.isExpelled()).thenReturn(expelled);
        when(oa.isSelfExpelled()).thenReturn(expelled);
        when(oa.parent()).thenReturn(OID.ROOT);
        when(ds.getOA_(soid)).thenReturn(oa);
        when(ds.getOANullable_(soid)).thenReturn(oa);
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
        when(cfgAggressiveChecking.get()).thenReturn(true);

        OA oaAdmitted2 = mock(OA.class);
        SIndex sidxAdmitted2 = new SIndex(99);
        SOID soidAdmitted2 = new SOID(sidxAdmitted2, oid);
        mockOA(oaAdmitted2, soidAdmitted2, false);

        when(stores.getAll_()).thenReturn(
                ImmutableSet.of(sidxAdmitted, sidxAdmitted2, sidxExpelled1, sidxExpelled2));
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

        assertNull(aol.locate_(oid, sidxAdmitted, type));
    }

    @Test
    public void shouldReturnAdmittedObjects() throws SQLException
    {
        assertEquals(aol.locate_(oid, sidxExpelled1, type), oaAdmitted);
    }
}
