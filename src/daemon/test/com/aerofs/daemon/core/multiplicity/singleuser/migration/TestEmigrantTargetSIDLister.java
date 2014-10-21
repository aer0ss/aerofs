package com.aerofs.daemon.core.multiplicity.singleuser.migration;

import com.aerofs.daemon.core.migration.EmigrantUtil;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserStoreHierarchy;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.sql.SQLException;
import java.util.List;

import static com.aerofs.daemon.core.mock.TestUtilCore.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class TestEmigrantTargetSIDLister extends AbstractTest
{
    @Mock IMapSID2SIndex sid2sidx;
    @Mock IMapSIndex2SID sidx2sid;
    @Mock SingleuserStoreHierarchy sss;

    @InjectMocks EmigrantTargetSIDLister emc;

    SID sidTarget = SID.generate();
    SID sidParent = SID.generate();
    SID sidGrandParent = SID.generate();
    SID sidGreatGrandParent = SID.generate();
    SIndex sidxTarget = new SIndex(1);
    SIndex sidxParent = new SIndex(2);
    SIndex sidxGrandParent = new SIndex(3);
    SIndex sidxGreatGrandParent = new SIndex(4);
    SIndex sidxRoot = new SIndex(5);

    SOID soid = new SOID(new SIndex(100), new OID(UniqueID.generate()));
    String name = EmigrantUtil.getDeletedObjectName_(soid, sidTarget);

    @Before
    public void setUp() throws SQLException, ExNotFound
    {
        mockStore(null, sidTarget, sidxTarget, sidxRoot, sss, null, sid2sidx, sidx2sid);
        mockStore(null, sidParent, sidxParent, sidxGrandParent, sss, null, sid2sidx, sidx2sid);
        mockStore(null, sidGrandParent, sidxGrandParent, sidxGreatGrandParent, sss, null, sid2sidx, sidx2sid);
        mockStore(null, sidGreatGrandParent, sidxGreatGrandParent, sidxRoot, sss, null, sid2sidx, sidx2sid);

        when(sss.getParent_(sidxTarget)).thenReturn(sidxParent);
        when(sss.getParent_(sidxParent)).thenReturn(sidxGrandParent);
        when(sss.getParent_(sidxGrandParent)).thenReturn(sidxGreatGrandParent);
        when(sss.getParent_(sidxGreatGrandParent)).thenReturn(sidxRoot);

        when(sss.isRoot_(sidxTarget)).thenReturn(false);
        when(sss.isRoot_(sidxParent)).thenReturn(false);
        when(sss.isRoot_(sidxGrandParent)).thenReturn(false);
        when(sss.isRoot_(sidxGreatGrandParent)).thenReturn(true);
    }

    @Test
    public void shouldReturnEmptySIDListForNonImmigrantName()
            throws SQLException
    {
        assertTrue(emc.getEmigrantTargetAncestorSIDsForMeta_(OID.TRASH, "abc").isEmpty());
    }

    @Test
    public void shouldReturnEmptySIDListForNonTrashParent()
            throws SQLException
    {
        assertTrue(emc.getEmigrantTargetAncestorSIDsForMeta_(new OID(UniqueID.generate()),
                name).isEmpty());
    }

    @Test
    public void shouldReturnEmptySIDListIfTargetStoreDoesntExist()
            throws SQLException, ExNotFound
    {
        mockAbsentStore(sidxTarget, sidTarget, null, sid2sidx, sidx2sid);
        assertTrue(emc.getEmigrantTargetAncestorSIDsForMeta_(OID.TRASH, name).isEmpty());
    }

    @Test
    public void shouldReturnNMinusOneAncestorsForLevelNTargetStore()
            throws SQLException
    {
        List<SID> list = emc.getEmigrantTargetAncestorSIDsForMeta_(OID.TRASH, name);
        assertEquals(list.size(), 3);
        assertEquals(list.get(0), sidParent);
        assertEquals(list.get(1), sidGrandParent);
        assertEquals(list.get(2), sidGreatGrandParent);
    }

    @Test
    public void shouldReturnOneAncestorForLevelTwoTargetStore()
            throws SQLException
    {
        when(sss.isRoot_(sidxParent)).thenReturn(true);
        List<SID> list = emc.getEmigrantTargetAncestorSIDsForMeta_(OID.TRASH, name);
        assertEquals(list.size(), 1);
        assertEquals(list.get(0), sidParent);
    }

    @Test
    public void shouldReturnOneAncestorForLevelOneTargetStore()
            throws SQLException
    {
        when(sss.isRoot_(sidxTarget)).thenReturn(true);
        List<SID> list = emc.getEmigrantTargetAncestorSIDsForMeta_(OID.TRASH, name);
        assertEquals(list.size(), 1);
        assertEquals(list.get(0), sidTarget);
    }
}
