/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.base.acl.Permissions;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.*;
import com.aerofs.daemon.core.store.IStoreJoiner.StoreInfo;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.SID;
import com.aerofs.lib.cfg.CfgRootSID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.ids.UserID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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

    private static StoreInfo sf(String name, boolean external)
    {
        return new StoreInfo(name, external,
                ImmutableMap.<UserID, Permissions>of(), ImmutableSet.of());
    }

    @Test
    public void joinStore_shouldJoinRootStore()
            throws Exception
    {
        SID rootSID = SID.rootSID(userID);
        when(sidx2sid.getNullable_(sidx)).thenReturn(rootSID);

        msj.joinStore_(sidx, rootSID, sf("test", false), t);

        verify(sc).createRootStore_(eq(rootSID), eq("test"), eq(t));
    }

    @Test
    public void joinStore_shouldJoinNonRootStore()
            throws Exception
    {
        SID sid = SID.generate();
        when(sidx2sid.getNullable_(sidx)).thenReturn(sid);

        msj.joinStore_(sidx, sid, sf("test", false), t);

        verify(sc).createRootStore_(eq(sid), eq("test"), eq(t));
    }

    @Test
    public void joinStore_shouldNotJoinOwnRootStore()
            throws Exception
    {
        SID rootSID = SID.rootSID(userID);
        when(sidx2sid.getNullable_(sidx)).thenReturn(rootSID);
        when(cfgRootSID.get()).thenReturn(rootSID);

        msj.joinStore_(sidx, rootSID, sf("test", false), t);

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
