package com.aerofs.daemon.core.lib;

import com.aerofs.base.acl.Permissions;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.*;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.cfg.CfgRootSID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.mockito.Mock;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class BaseStoreJoinerTest extends AbstractTest
{
    private @Mock IMapSIndex2SID sidx2sid;
    private @Mock StoreCreator sc;
    private @Mock Trans t;
    private @Mock CfgRootSID cfgRootSID;
    private @Mock StoreHierarchy stores;

    private @Mock StoreDeleter sd;
    protected IStoreJoiner isj;

    UserID userID = UserID.fromInternal("test@gmail");
    SIndex sidx = new SIndex(123);

    protected static IStoreJoiner.StoreInfo sf(String name, boolean external)
    {
        return new IStoreJoiner.StoreInfo(name, external,
                ImmutableMap.<UserID, Permissions>of(), ImmutableSet.of());
    }

    public void joinStore_shouldJoinRootStore() throws Exception
    {
        SID rootSID = SID.rootSID(userID);
        when(sidx2sid.getNullable_(sidx)).thenReturn(rootSID);

        isj.joinStore_(sidx, rootSID, sf("test", false), t);

        verify(sc).createRootStore_(eq(rootSID), eq("test"), eq(t));
    }

    public void joinStore_shouldJoinNonRootStore() throws Exception
    {
        SID sid = SID.generate();
        when(sidx2sid.getNullable_(sidx)).thenReturn(sid);

        isj.joinStore_(sidx, sid, sf("test", false), t);

        verify(sc).createRootStore_(eq(sid), eq("test"), eq(t));
    }

    public void joinStore_shouldNotJoinOwnRootStore() throws Exception
    {
        SID rootSID = SID.rootSID(userID);
        when(sidx2sid.getNullable_(sidx)).thenReturn(rootSID);
        when(cfgRootSID.get()).thenReturn(rootSID);
        isj.joinStore_(sidx, rootSID, sf("test", false), t);

        verifyZeroInteractions(sc);
    }

    @Test
    public void leaveStore_shouldLeaveRootStore() throws Exception
    {
        SID sid = SID.generate();
        when(sidx2sid.getNullable_(sidx)).thenReturn(sid);
        when(stores.isRoot_(sidx)).thenReturn(true);
        isj.leaveStore_(sidx, sid, t);

        verify(sd).deleteRootStore_(sidx, PhysicalOp.APPLY, t);
    }

    @Test
    public void leaveStore_shouldNotLeaveNonRootStore() throws Exception
    {
        SID sid = SID.generate();
        when(sidx2sid.getNullable_(sidx)).thenReturn(sid);
        when(stores.isRoot_(sidx)).thenReturn(false);
        isj.leaveStore_(sidx, sid, t);

        verifyZeroInteractions(sd);
    }

    @Test
    public void leaveStore_shouldNotLeaveAbsentStore() throws Exception
    {
        SID sid = SID.generate();
        when(sidx2sid.getNullable_(sidx)).thenReturn(null);
        when(stores.isRoot_(sidx)).thenReturn(true);
        isj.leaveStore_(sidx, sid, t);

        verifyZeroInteractions(sd);
    }
}
