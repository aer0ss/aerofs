/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.id.SID;
import com.aerofs.lib.cfg.CfgRootSID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.base.id.UserID;
import com.aerofs.testlib.AbstractTest;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TestMultiuserStoreJoiner extends AbstractTest
{
    @Mock CfgRootSID cfgRootSID;
    @Mock StoreHierarchy stores;
    @Mock StoreDeleter sd;
    @Mock StoreCreator sc;
    @Mock IMapSIndex2SID sidx2sid;
    @Mock Trans t;

    @InjectMocks MultiuserStoreJoiner msj;

    SIndex sidx = new SIndex(123);

    UserID userID = UserID.fromInternal("test@gmail");

    @Test
    public void joinStore_shouldJoinRootStore()
            throws Exception
    {
        SID rootSID = SID.rootSID(userID);
        when(sidx2sid.getNullable_(sidx)).thenReturn(rootSID);

        msj.joinStore_(sidx, rootSID, "test", false, t);

        verify(sc).createRootStore_(eq(rootSID), eq("test"), eq(t));
    }

    @Test
    public void joinStore_shouldJoinNonRootStore()
            throws Exception
    {
        SID sid = SID.generate();
        when(sidx2sid.getNullable_(sidx)).thenReturn(sid);

        msj.joinStore_(sidx, sid, "test", false, t);

        verify(sc).createRootStore_(eq(sid), eq("test"), eq(t));
    }

    @Test
    public void joinStore_shouldNotJoinOwnRootStore()
            throws Exception
    {
        SID rootSID = SID.rootSID(userID);
        when(sidx2sid.getNullable_(sidx)).thenReturn(rootSID);
        when(cfgRootSID.get()).thenReturn(rootSID);

        msj.joinStore_(sidx, rootSID, "test", false, t);

        verifyZeroInteractions(sc);
    }

    @Test
    public void leaveStore_shouldLeaveRootStore()
            throws Exception
    {
        SID sid = SID.generate();
        when(sidx2sid.getNullable_(sidx)).thenReturn(sid);
        when(stores.isRoot_(sidx)).thenReturn(true);
        msj.leaveStore_(sidx, sid, t);

        verify(sd).deleteRootStore_(sidx, PhysicalOp.APPLY, t);
    }

    @Test
    public void leaveStore_shouldNotLeaveNonRootStore()
            throws Exception
    {
        SID sid = SID.generate();
        when(sidx2sid.getNullable_(sidx)).thenReturn(sid);
        when(stores.isRoot_(sidx)).thenReturn(false);
        msj.leaveStore_(sidx, sid, t);

        verifyZeroInteractions(sd);
    }

    @Test
    public void leaveStore_shouldNotLeaveAbsentStore()
            throws Exception
    {
        SID sid = SID.generate();
        when(sidx2sid.getNullable_(sidx)).thenReturn(null);
        when(stores.isRoot_(sidx)).thenReturn(true);
        msj.leaveStore_(sidx, sid, t);

        verifyZeroInteractions(sd);
    }
}
