/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.ds.ObjectSurgeon;
import com.aerofs.daemon.core.notification.RitualNotificationServer;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgRootSID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.os.OSUtil;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(OSUtil.class)
public class TestSingleuserStoreJoiner
{
    @Mock Trans t;

    @Mock SingleuserStores stores;
    @Mock ObjectCreator oc;
    @Mock ObjectDeleter od;
    @Mock ObjectSurgeon os;
    @Mock StoreDeleter sd;
    @Mock DirectoryService ds;
    @Mock CfgRootSID cfgRootSID;
    @Mock RitualNotificationServer rns;
    @Mock SharedFolderAutoLeaver lod;
    @Mock IMapSIndex2SID sidx2sid;

    @InjectMocks SingleuserStoreJoiner ssj;


    SIndex sidx = new SIndex(123);

    UserID userID = UserID.fromInternal("test@gmail");

    SIndex rootSidx = new SIndex(1);
    SID rootSID = SID.rootSID(userID);

    @Before
    public void setUp()
    {
        when(cfgRootSID.get()).thenReturn(rootSID);
        when(stores.getUserRoot_()).thenReturn(rootSidx);
    }

    private void verifyAnchorCreated(SID sid, String name) throws Exception
    {
        verify(oc).createMeta_(eq(Type.ANCHOR), eq(new SOID(rootSidx, SID.storeSID2anchorOID(sid))),
                eq(OID.ROOT), eq(name), eq(0), eq(PhysicalOp.APPLY), eq(false), eq(false), eq(t));
    }

    @Test
    public void joinStore_shouldNotJoinOwnRootStore() throws Exception
    {
        ssj.joinStore_(rootSidx, rootSID, "test", false, t);

        verifyZeroInteractions(oc, od, os, sd, lod, rns);
    }

    @Test
    public void joinStore_shouldJoinNonRootStore() throws Exception
    {
        SID sid = SID.generate();
        ssj.joinStore_(sidx, sid, "test", false, t);

        verify(lod).removeFromQueue_(sid, t);
        verifyAnchorCreated(sid, "test");
        verifyZeroInteractions(od, os, sd);
    }

    @Test
    public void joinStore_shouldNotCleanStoreNameWhenJoiningOnNix() throws Exception
    {
        SID sid = SID.generate();

        PowerMockito.mockStatic(OSUtil.class);
        Mockito.when(OSUtil.isWindows()).thenReturn(false);

        ssj.joinStore_(sidx, sid, "*test", false, t);

        verify(lod).removeFromQueue_(sid, t);
        verifyAnchorCreated(sid, "*test");

        verifyZeroInteractions(od, os, sd);
    }

    @Test
    public void joinStore_shouldCleanStoreNameWhenJoiningOnWin() throws Exception
    {
        SID sid = SID.generate();

        // pretend to be on windows to test name-cleaning
        PowerMockito.mockStatic(OSUtil.class);
        Mockito.when(OSUtil.isWindows()).thenReturn(true);

        ssj.joinStore_(sidx, sid, "*test", false, t);

        verify(lod).removeFromQueue_(sid, t);
        verifyAnchorCreated(sid, "_test");

        verifyZeroInteractions(od, os, sd);
    }

    @Test
    public void leaveStore_shouldNotLeaveOwnRootStore() throws Exception
    {
        ssj.leaveStore_(rootSidx, rootSID, t);

        verifyZeroInteractions(oc, od, os, sd, lod, rns);
    }

    @Test
    public void leaveStore_shouldLeaveNonRootStore() throws Exception
    {
        SID sid = SID.generate();

        SOID soid = new SOID(sidx, SID.storeSID2anchorOID(sid));
        OA oa = mock(OA.class);
        when(stores.getAll_()).thenReturn(ImmutableSet.of(rootSidx, sidx));
        when(ds.getOANullable_(eq(soid))).thenReturn(oa);
        when(oa.type()).thenReturn(Type.ANCHOR);
        when(oa.soid()).thenReturn(soid);
        when(ds.resolve_(oa)).thenReturn(Path.fromString(rootSID, "foo"));

        ssj.leaveStore_(sidx, sid, t);

        verify(od).delete_(soid, PhysicalOp.APPLY, t);
    }

    @Test
    public void leaveStore_shouldLeaveRootStore() throws Exception
    {
        SID sid = SID.generate();

        when(stores.getAll_()).thenReturn(ImmutableSet.of(rootSidx, sidx));
        when(stores.isRoot_(sidx)).thenReturn(true);
        when(sidx2sid.getNullable_(sidx)).thenReturn(sid);

        ssj.leaveStore_(sidx, sid, t);

        verify(sd).deleteRootStore_(sidx, t);
    }
}
