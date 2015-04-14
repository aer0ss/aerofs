/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.lib.BaseStoreJoinerTest;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.cfg.CfgRootSID;
import com.aerofs.lib.id.SIndex;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Mockito.*;

public class TestMultiuserStoreJoiner extends BaseStoreJoinerTest
{
    @Mock CfgRootSID cfgRootSID;
    @Mock StoreHierarchy stores;
    @Mock StoreDeleter sd;

    @InjectMocks MultiuserStoreJoiner msj;

    SIndex sidx = new SIndex(123);

    UserID userID = UserID.fromInternal("test@gmail");

    @Before
    public void setUp() throws Exception
    {
        isj = msj;
    }

    @Test
    public void joinStore_shouldJoinRootStore() throws Exception
    {
        super.joinStore_shouldJoinRootStore();
    }

    @Test
    public void joinStore_shouldJoinNonRootStore() throws Exception
    {
        super.joinStore_shouldJoinNonRootStore();
    }

    @Test
    public void joinStore_shouldNotJoinOwnRootStore() throws Exception
    {
        SID rootSID = SID.rootSID(userID);
        when(sidx2sid.getNullable_(sidx)).thenReturn(rootSID);
        when(cfgRootSID.get()).thenReturn(rootSID);

        msj.joinStore_(sidx, rootSID, sf("test", false), t);

        verifyZeroInteractions(sc);
    }

    @Test
    public void leaveStore_shouldLeaveRootStore() throws Exception
    {
        SID sid = SID.generate();
        when(sidx2sid.getNullable_(sidx)).thenReturn(sid);
        when(stores.isRoot_(sidx)).thenReturn(true);
        msj.leaveStore_(sidx, sid, t);

        verify(sd).deleteRootStore_(sidx, PhysicalOp.APPLY, t);
    }

    @Test
    public void leaveStore_shouldNotLeaveNonRootStore() throws Exception
    {
        SID sid = SID.generate();
        when(sidx2sid.getNullable_(sidx)).thenReturn(sid);
        when(stores.isRoot_(sidx)).thenReturn(false);
        msj.leaveStore_(sidx, sid, t);

        verifyZeroInteractions(sd);
    }

    @Test
    public void leaveStore_shouldNotLeaveAbsentStore() throws Exception
    {
        SID sid = SID.generate();
        when(sidx2sid.getNullable_(sidx)).thenReturn(null);
        when(stores.isRoot_(sidx)).thenReturn(true);
        msj.leaveStore_(sidx, sid, t);

        verifyZeroInteractions(sd);
    }
}
