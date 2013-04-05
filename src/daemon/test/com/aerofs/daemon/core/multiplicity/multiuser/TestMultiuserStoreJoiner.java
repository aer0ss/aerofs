/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.core.store.StoreDeleter;
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
    @Mock IStores stores;
    @Mock StoreDeleter sd;
    @Mock StoreCreator sc;
    @Mock Trans t;

    @InjectMocks MultiuserStoreJoiner msj;

    SIndex sidx = new SIndex(123);

    UserID userID = UserID.fromInternal("test@gmail");

    @Test
    public void joinStore_shouldJoinRootStore()
            throws Exception
    {
        SID rootSID = SID.rootSID(userID);
        msj.joinStore_(sidx, rootSID, "test", false, t);

        verify(sc).createRootStore_(eq(rootSID), eq(t));
    }

    @Test
    public void joinStore_shouldJoinNonRootStore()
            throws Exception
    {
        SID rootSID = SID.generate();
        msj.joinStore_(sidx, rootSID, "test", false, t);

        verify(sc).createRootStore_(eq(rootSID), eq(t));
    }

    @Test
    public void joinStore_shouldNotJoinOwnRootStore()
            throws Exception
    {
        SID rootSID = SID.rootSID(userID);
        when(cfgRootSID.get()).thenReturn(rootSID);
        msj.joinStore_(sidx, rootSID, "test", false, t);

        verifyZeroInteractions(sc);
    }

    @Test
    public void leaveStore_shouldLeaveRootStore()
            throws Exception
    {
        SID sid = SID.generate();
        when(stores.isRoot_(sidx)).thenReturn(true);
        msj.leaveStore_(sidx, sid, t);

        verify(sd).deleteRootStore_(sidx, t);
    }

    @Test
    public void leaveStore_shouldNotLeaveNonRootStore()
            throws Exception
    {
        SID sid = SID.generate();
        when(stores.isRoot_(sidx)).thenReturn(false);
        msj.leaveStore_(sidx, sid, t);

        verifyZeroInteractions(sd);
    }
}
