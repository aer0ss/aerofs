package com.aerofs.daemon.core.sumu.singleuser.migration;

import com.aerofs.daemon.core.migration.EmigrantUtil;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UniqueID;
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

public class TestEmigrantCreator extends AbstractTest
{
    @Mock IMapSID2SIndex sid2sidx;
    @Mock IMapSIndex2SID sidx2sid;
    @Mock IStores ss;

    @InjectMocks EmigrantCreator emc;

    SID sidTarget = new SID(UniqueID.generate());
    SID sidParent = new SID(UniqueID.generate());
    SID sidGrandParent = new SID(UniqueID.generate());
    SID sidGreatGrandParent = new SID(UniqueID.generate());
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
        mockStore(null, sidTarget, sidxTarget, sidxRoot, ss, null, sid2sidx, sidx2sid);
        mockStore(null, sidParent, sidxParent, sidxGrandParent, ss, null, sid2sidx, sidx2sid);
        mockStore(null, sidGrandParent, sidxGrandParent, sidxGreatGrandParent, ss, null, sid2sidx, sidx2sid);
        mockStore(null, sidGreatGrandParent, sidxGreatGrandParent, sidxRoot, ss, null, sid2sidx, sidx2sid);

        when(ss.getParent_(sidxTarget)).thenReturn(sidxParent);
        when(ss.getParent_(sidxParent)).thenReturn(sidxGrandParent);
        when(ss.getParent_(sidxGrandParent)).thenReturn(sidxGreatGrandParent);
        when(ss.getParent_(sidxGreatGrandParent)).thenReturn(sidxRoot);

        when(ss.isRoot_(sidxTarget)).thenReturn(false);
        when(ss.isRoot_(sidxParent)).thenReturn(false);
        when(ss.isRoot_(sidxGrandParent)).thenReturn(false);
        when(ss.isRoot_(sidxGreatGrandParent)).thenReturn(true);
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
        when(ss.isRoot_(sidxParent)).thenReturn(true);
        List<SID> list = emc.getEmigrantTargetAncestorSIDsForMeta_(OID.TRASH, name);
        assertEquals(list.size(), 1);
        assertEquals(list.get(0), sidParent);
    }

    @Test
    public void shouldReturnOneAncestorForLevelOneTargetStore()
            throws SQLException
    {
        when(ss.isRoot_(sidxTarget)).thenReturn(true);
        List<SID> list = emc.getEmigrantTargetAncestorSIDsForMeta_(OID.TRASH, name);
        System.out.println(list.size());
        assertEquals(list.size(), 1);
        assertEquals(list.get(0), sidTarget);
    }
}
