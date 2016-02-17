/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.base.acl.Permissions;
import com.aerofs.daemon.core.polaris.PolarisAsyncClient;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase;
import com.aerofs.daemon.core.store.IStoreJoiner.StoreInfo;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.ds.ObjectSurgeon;
import com.aerofs.daemon.core.ds.ResolvedPathTestUtil;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.lib.db.UnlinkedRootDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.cfg.CfgRootSID;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.aerofs.ritual_notification.RitualNotifier;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TestSingleuserStoreJoiner extends AbstractTest
{
    @Mock Trans t;

    @Mock SingleuserStoreHierarchy stores;
    @Mock ObjectCreator oc;
    @Mock ObjectDeleter od;
    @Mock ObjectSurgeon os;
    @Mock StoreDeleter sd;
    @Mock DirectoryService ds;
    @Mock CfgRootSID cfgRootSID;
    @Mock RitualNotificationServer rns;
    @Mock SharedFolderAutoUpdater lod;
    @Mock IMapSIndex2SID sidx2sid;
    @Mock IMapSID2SIndex sid2sidx;
    @Mock UnlinkedRootDatabase urdb;
    @Mock RitualNotifier _ritualNotifier;
    @Mock CfgUsePolaris usePolaris;
    @Mock PolarisAsyncClient polaris;
    @Mock RemoteLinkDatabase rldb;

    @InjectMocks SingleuserStoreJoiner ssj;

    SIndex sidx = new SIndex(123);

    UserID userID = UserID.fromInternal("test@gmail");

    SIndex rootSidx = new SIndex(1);
    SID rootSID = SID.rootSID(userID);

    @Before
    public void setUp()
    {
        when(cfgRootSID.get()).thenReturn(rootSID);
        when(sid2sidx.get_(rootSID)).thenReturn(rootSidx);
        when(rns.getRitualNotifier()).thenReturn(_ritualNotifier);
    }

    private void verifyAnchorCreated(SID sid, String name) throws Exception
    {
        verify(oc).createMeta_(eq(Type.ANCHOR), eq(new SOID(rootSidx, SID.storeSID2anchorOID(sid))),
                eq(OID.ROOT), eq(name), eq(PhysicalOp.APPLY), eq(false), eq(false), eq(t));
    }

    private static StoreInfo sf(String name, boolean external)
    {
        return new StoreInfo(name, external,
                ImmutableMap.<UserID, Permissions>of(), ImmutableSet.of());
    }

    @Test
    public void joinStore_shouldNotJoinOwnRootStore() throws Exception
    {
        ssj.joinStore_(rootSidx, rootSID, sf("test", false), t);

        verifyZeroInteractions(oc, od, os, sd, lod, rns, urdb);
    }

    @Test
    public void joinStore_shouldNotJoinExternalStore() throws Exception
    {
        SID sid = SID.generate();
        ssj.joinStore_(sidx, sid, sf("test", true), t);

        verify(lod).removeLeaveCommandsFromQueue_(sid, t);
        verify(urdb).addUnlinkedRoot(sid, "test", t);

        verifyZeroInteractions(oc, od, os, sd);
    }

    @Test
    public void joinStore_shouldJoinNonRootStore() throws Exception
    {
        SID sid = SID.generate();
        ssj.joinStore_(sidx, sid, sf("test", false), t);

        verify(lod).removeLeaveCommandsFromQueue_(sid, t);
        verifyAnchorCreated(sid, "test");
        verifyZeroInteractions(od, os, sd, urdb);
    }

    @Test
    public void joinStore_shouldNotCleanStoreNameWhenJoiningOnNix() throws Exception
    {
        SID sid = SID.generate();

        ssj.joinStore_(sidx, sid, sf("*test", false), t);

        verify(lod).removeLeaveCommandsFromQueue_(sid, t);
        verifyAnchorCreated(sid, "*test");

        verifyZeroInteractions(od, os, sd, urdb);
    }

    @Test
    public void leaveStore_shouldNotLeaveOwnRootStore() throws Exception
    {
        ssj.leaveStore_(rootSidx, rootSID, t);

        verifyZeroInteractions(oc, od, os, sd, lod, rns, urdb);
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
        when(ds.resolve_(oa)).thenReturn(ResolvedPathTestUtil.fromString(rootSID, "foo"));

        ssj.leaveStore_(sidx, sid, t);

        verify(urdb).removeUnlinkedRoot(sid, t);
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

        verify(urdb).removeUnlinkedRoot(sid, t);
        verify(sd).deleteRootStore_(sidx, PhysicalOp.APPLY, t);
    }

    // TODO: polaris tests
}
