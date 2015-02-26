/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.acl;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.acl.SubjectPermissions;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.store.AbstractStoreJoiner;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.ACLDatabase;
import com.aerofs.daemon.lib.db.IACLDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.ids.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.ids.UserID;
import com.aerofs.proto.Common.PBPermissions;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(value = Parameterized.class)
public class TestACLSynchronizer extends AbstractTest
{
    @Mock TokenManager tokenManager;
    @Mock Token tk;
    @Mock TCB tcb;
    @Mock Trans t;
    @Mock TransManager tm;
    @Mock DirectoryService ds;
    @Mock AbstractStoreJoiner storeJoiner;
    @Mock SIDMap sm;
    @Mock CfgLocalUser cfgLocalUser;
    @Mock SPBlockingClient spClient;
    @Mock InjectableSPBlockingClientFactory factSP;

    // use in-memory db to store local ACL
    InMemorySQLiteDBCW idbcw = new InMemorySQLiteDBCW();
    IACLDatabase adb = new ACLDatabase(idbcw.getCoreDBCW());

    // mix of real objects and mocks don't work well with @InjectMocks...
    ACLFilter filter = spy(new ACLFilter(adb));
    LocalACL lacl;
    ACLSynchronizer aclsync;

    UserID user1 = UserID.fromInternal("user1@foo.bar");
    UserID user2 = UserID.fromInternal("user2@foo.bar");

    SID sid1 = SID.generate();

    private final boolean external;
    private final String SHARED_FOLDER_NAME = "shared";

    public TestACLSynchronizer(boolean external)
    {
        this.external = external;
    }

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][]{
                        {false},
                        {true},
                });
    }

    @Before
    public void setUp() throws Exception
    {
        AppRoot.set("/foo/bar");

        idbcw.init_();

        when(cfgLocalUser.get()).thenReturn(user1);

        when(factSP.create()).thenReturn(spClient);
        when(spClient.signInRemote()).thenReturn(spClient);

        when(tm.begin_()).thenReturn(t);
        when(tokenManager.acquire_(any(Cat.class), anyString())).thenReturn(tk);
        when(tk.pseudoPause_(anyString())).thenReturn(tcb);

        lacl = new LocalACL(cfgLocalUser, tm, sm, adb, ds);

        aclsync = new ACLSynchronizer(tokenManager, tm, filter, lacl, storeJoiner,
                sm, sm, cfgLocalUser, factSP);
    }

    @After
    public void tearDown() throws Exception
    {
        idbcw.fini_();
    }

    private PBStoreACL storeACL(SID sid, SubjectPermissions... roles)
    {
        PBStoreACL.Builder bd = PBStoreACL.newBuilder()
                .setStoreId(BaseUtil.toPB(sid))
                .setExternal(external)
                .setName(SHARED_FOLDER_NAME);
        for (SubjectPermissions r : roles) bd.addSubjectPermissions(r.toPB());
        return bd.build();
    }

    private void mockGetACL(long epoch, PBStoreACL... acls) throws Exception
    {
        GetACLReply.Builder bd = GetACLReply.newBuilder().setEpoch(epoch);
        for (PBStoreACL acl : acls) bd.addStoreAcl(acl);
        when(spClient.getACL(anyLong())).thenReturn(bd.build());
    }

    @Test
    public void shouldNotGetACLOnSameEpoch() throws Exception
    {
        adb.setEpoch_(10L, t);

        aclsync.syncToLocal_(10L);

        verifyNoMoreInteractions(spClient);
    }

    @Test
    public void shouldGetACLOnDifferentEpoch() throws Exception
    {
        adb.setEpoch_(10L, t);
        when(spClient.getACL(anyLong()))
                .thenReturn(GetACLReply.newBuilder().setEpoch(15L).build());

        aclsync.syncToLocal_(42L);

        verify(spClient).signInRemote();
        verify(spClient).getACL(10L);
        assertEquals(15L, adb.getEpoch_());
    }

    @Test
    public void shouldUpdateLocalACLOnSuccessfulRemoteUpdate() throws Exception
    {
        SIndex sidx = new SIndex(2);
        mockPresent(sidx, sid1);

        aclsync.update_(sidx, user1, Permissions.allOf(Permission.WRITE), false);

        verify(spClient).updateACL(eq(BaseUtil.toPB(sid1)), any(String.class), any(PBPermissions.class),
                any(Boolean.class));
        assertEquals(ImmutableMap.of(user1, Permissions.allOf(Permission.WRITE)), lacl.get_(sidx));
    }

    @Test
    public void shouldNotUpdateLocalACLOnFailedRemoteUpdate() throws Exception
    {
        SIndex sidx = new SIndex(2);
        mockPresent(sidx, sid1);

        when(spClient.updateACL(any(ByteString.class), any(String.class), any(PBPermissions.class), any(Boolean.class)))
                .thenThrow(new ExNoPerm());

        boolean ok = false;
        try {
            aclsync.update_(sidx, user1, Permissions.allOf(Permission.WRITE), false);
        } catch (ExNoPerm e) {
            ok = true;
        }
        assertTrue(ok);

        verify(spClient).updateACL(eq(BaseUtil.toPB(sid1)), any(String.class), any(PBPermissions.class), any(Boolean.class));
        assertTrue(lacl.get_(sidx).isEmpty());
    }

    @Test
    public void shouldDeleteLocalACLOnSuccessfulRemoteDelete() throws Exception
    {
        SIndex sidx = new SIndex(2);
        mockPresent(sidx, sid1);
        lacl.set_(sidx, ImmutableMap.of(user1, Permissions.allOf(Permission.WRITE)), t);

        aclsync.delete_(sidx, user1);

        verify(spClient).deleteACL(eq(BaseUtil.toPB(sid1)), any(String.class));
        assertTrue(lacl.get_(sidx).isEmpty());
    }

    @Test
    public void shouldNotDeleteLocalACLOnFailedRemoteDelete() throws Exception
    {
        SIndex sidx = new SIndex(2);
        mockPresent(sidx, sid1);
        lacl.set_(sidx, ImmutableMap.of(user1, Permissions.allOf(Permission.WRITE)), t);

        when(spClient.deleteACL(any(ByteString.class), any(String.class)))
                .thenThrow(new ExNoPerm());

        boolean ok = false;
        try {
            aclsync.delete_(sidx, user1);
        } catch (ExNoPerm e) {
            ok = true;
        }
        assertTrue(ok);

        verify(spClient).deleteACL(eq(BaseUtil.toPB(sid1)), any(String.class));
        assertEquals(ImmutableMap.of(user1, Permissions.allOf(Permission.WRITE)), lacl.get_(sidx));
    }

    @Test
    public void shouldJoinFolderWhenAccessGained() throws Exception
    {
        SIndex sidx = new SIndex(2);
        mockAbsent(sidx, sid1);

        mockGetACL(42L, storeACL(sid1,
                new SubjectPermissions(user1, Permissions.allOf(Permission.WRITE, Permission.MANAGE)),
                new SubjectPermissions(user2, Permissions.allOf(Permission.WRITE))));

        aclsync.syncToLocal_();

        verify(spClient).getACL(anyLong());

        verify(storeJoiner).joinStore_(eq(sidx), eq(sid1), eq(SHARED_FOLDER_NAME), eq(external),
                eq(t));
    }

    private void mockPresent(SIndex sidx, SID sid) throws SQLException
    {
        when(sm.get_(eq(sidx))).thenReturn(sid);
        when(sm.getNullable_(eq(sidx))).thenReturn(sid);
        when(sm.getLocalOrAbsent_(eq(sidx))).thenReturn(sid);
        when(sm.get_(sid)).thenReturn(sidx);
        when(sm.getNullable_(sid)).thenReturn(sidx);
        when(sm.getLocalOrAbsentNullable_(sid)).thenReturn(sidx);
    }

    private void mockAbsent(SIndex sidx, SID sid) throws SQLException
    {
        when(sm.get_(eq(sidx))).thenThrow(new AssertionError());
        when(sm.getNullable_(eq(sidx))).thenReturn(null);
        when(sm.getLocalOrAbsent_(eq(sidx))).thenReturn(sid);
        when(sm.get_(sid)).thenThrow(new AssertionError());
        when(sm.getNullable_(sid)).thenReturn(null);
        when(sm.getLocalOrAbsentNullable_(sid)).thenReturn(sidx);
    }

    @Test
    public void shouldLeaveFolderWhenAccessLost() throws Exception
    {
        SIndex sidx = new SIndex(2);
        mockPresent(sidx, sid1);

        lacl.set_(sidx, ImmutableMap.of(user1, Permissions.allOf(Permission.WRITE)), t);
        mockGetACL(42L);

        aclsync.syncToLocal_();

        verify(spClient).getACL(anyLong());
        verify(storeJoiner).leaveStore_(sidx, sid1, t);
    }

    @Test
    public void shouldNotJoinFolderWhenAccessGainedForUserOutOfShard() throws Exception
    {
        SIndex sidx = new SIndex(2);
        mockAbsent(sidx, sid1);

        mockGetACL(42L, storeACL(sid1,
                new SubjectPermissions(user1, Permissions.allOf(Permission.WRITE, Permission.MANAGE)),
                new SubjectPermissions(user2, Permissions.allOf(Permission.WRITE))));
        when(filter.shouldKeep_(anySetOf(UserID.class))).thenReturn(false);

        aclsync.syncToLocal_();

        verify(spClient).getACL(anyLong());

        verify(storeJoiner, never()).joinStore_(eq(sidx), eq(sid1), eq(SHARED_FOLDER_NAME),
                eq(external), eq(t));
    }

    @Test
    public void shouldLeaveFolderWhenUserRemovedFromShard() throws Exception
    {
        SIndex sidx = new SIndex(2);
        mockPresent(sidx, sid1);

        lacl.set_(sidx, ImmutableMap.of(user1, Permissions.allOf(Permission.WRITE)), t);
        mockGetACL(42L, storeACL(sid1,
                new SubjectPermissions(user1, Permissions.allOf(Permission.WRITE, Permission.MANAGE)),
                new SubjectPermissions(user2, Permissions.allOf(Permission.WRITE))));
        when(filter.shouldKeep_(anySetOf(UserID.class))).thenReturn(false);

        aclsync.syncToLocal_();

        verify(spClient).getACL(anyLong());
        verify(storeJoiner).leaveStore_(sidx, sid1, t);
    }

    @Test
    public void shouldNotJoinOrLeaveFolderWhenAccessUnchanged() throws Exception
    {
        SIndex sidx = new SIndex(2);
        mockPresent(sidx, sid1);
        lacl.set_(sidx, ImmutableMap.of(user1, Permissions.allOf(Permission.WRITE)), t);

        mockGetACL(42L, storeACL(sid1,
                new SubjectPermissions(user1, Permissions.allOf(Permission.WRITE, Permission.MANAGE)),
                new SubjectPermissions(user2, Permissions.allOf(Permission.WRITE))));

        aclsync.syncToLocal_();

        verify(spClient).getACL(anyLong());
        verifyNoMoreInteractions(storeJoiner);
    }

    @Test
    public void shouldLeaveExpelledFolderWhenAccessLost() throws Exception
    {
        SIndex sidx = new SIndex(2);
        mockAbsent(sidx, sid1);

        lacl.set_(sidx, ImmutableMap.of(user1, Permissions.allOf(Permission.WRITE)), t);
        mockGetACL(42L);

        aclsync.syncToLocal_();

        verify(spClient).getACL(anyLong());
        verify(storeJoiner).leaveStore_(sidx, sid1, t);
    }
}
