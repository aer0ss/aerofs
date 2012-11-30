/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.acl;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStoreJoiner;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.ACLDatabase;
import com.aerofs.daemon.lib.db.IACLDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.lib.id.UserID;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.proto.Sp.GetSharedFolderNamesReply;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.URL;
import java.util.Collections;
import java.util.Iterator;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class TestACLSynchronizer extends AbstractTest
{
    @Mock TC tc;
    @Mock Token tk;
    @Mock TCB tcb;
    @Mock Trans t;
    @Mock TransManager tm;
    @Mock IStores stores;
    @Mock DirectoryService ds;
    @Mock IStoreJoiner storeJoiner;
    @Mock IMapSID2SIndex sid2sidx;
    @Mock IMapSIndex2SID sidx2sid;
    @Mock CfgLocalUser cfgLocalUser;
    @Mock SPBlockingClient spClient;
    @Mock SPBlockingClient.Factory factSP;

    // use in-memory db to store local ACL
    InMemorySQLiteDBCW idbcw = new InMemorySQLiteDBCW();
    IACLDatabase adb = new ACLDatabase(idbcw.getCoreDBCW());

    // mix of real objects and mocks don't work well with @InjectMocks...
    LocalACL lacl;
    ACLSynchronizer aclsync;

    UserID user1 = UserID.fromInternal("user1@foo.bar");
    UserID user2 = UserID.fromInternal("user2@foo.bar");

    SID sid1 = new SID(UniqueID.generate());

    @Before
    public void setUp() throws Exception
    {
        idbcw.init_();

        when(cfgLocalUser.get()).thenReturn(user1);

        when(factSP.create_(any(UserID.class))).thenReturn(spClient);
        when(factSP.create_(any(URL.class), any(UserID.class))).thenReturn(spClient);

        when(tm.begin_()).thenReturn(t);
        when(tc.acquireThrows_(any(Cat.class), anyString())).thenReturn(tk);
        when(tk.pseudoPause_(anyString())).thenReturn(tcb);


        lacl = new LocalACL(cfgLocalUser, ds, tm, stores, adb);

        aclsync = new ACLSynchronizer(tc, tm, adb, lacl, stores, storeJoiner,
                sidx2sid, sid2sidx, cfgLocalUser, factSP);
    }

    @After
    public void tearDown() throws Exception
    {
        idbcw.fini_();
    }

    private PBStoreACL storeACL(SID sid, SubjectRolePair... roles)
    {
        PBStoreACL.Builder bd = PBStoreACL.newBuilder().setStoreId(sid.toPB());
        for (SubjectRolePair r : roles) bd.addSubjectRole(r.toPB());
        return bd.build();
    }

    private void mockGetACL(long epoch, PBStoreACL... acls) throws Exception
    {
        GetACLReply.Builder bd = GetACLReply.newBuilder().setEpoch(epoch);
        for (PBStoreACL acl : acls) bd.addStoreAcl(acl);
        when(spClient.getACL(anyLong())).thenReturn(bd.build());
    }

    @SuppressWarnings("unchecked")
    private static <T> Iterable<T> anyIterable(Class<T> c)
    {
        return (Iterable<T>)any(Iterable.class);
    }

    private void mockGetSharedFolderNames(final SID sid, final String name) throws Exception
    {
        when(spClient.getSharedFolderNames(anyIterable(ByteString.class)))
                .thenAnswer(new Answer<GetSharedFolderNamesReply>()
                {
                    @Override
                    @SuppressWarnings("unchecked")
                    public GetSharedFolderNamesReply answer(InvocationOnMock invocation)
                            throws Throwable
                    {
                        Object[] args = invocation.getArguments();
                        assert args.length == 1 && args[0] instanceof Iterable;
                        Iterator<ByteString> it = ((Iterable<ByteString>)args[0]).iterator();
                        assert sid.equals(new SID(it.next()));
                        return GetSharedFolderNamesReply.newBuilder().addFolderName(name).build();
                    }
                });
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
        when(stores.getAll_()).thenReturn(Collections.<SIndex>emptySet());
        when(spClient.getACL(anyLong())).thenReturn(GetACLReply.newBuilder().setEpoch(15L).build());

        aclsync.syncToLocal_(42L);

        verify(spClient).signInRemote();
        verify(spClient).getACL(10L);
        assertEquals(15L, adb.getEpoch_());
    }

    @Test
    public void shouldUpdateLocalACLOnSuccessfulRemoteUpdate() throws Exception
    {
        SIndex sidx = new SIndex(2);
        when(sidx2sid.get_(eq(sidx))).thenReturn(sid1);
        when(sidx2sid.getNullable_(eq(sidx))).thenReturn(sid1);

        aclsync.update_(sidx, ImmutableMap.of(user1, Role.EDITOR));

        verify(spClient).updateACL(eq(sid1.toPB()), anyIterable(PBSubjectRolePair.class));
        assertEquals(ImmutableMap.of(user1, Role.EDITOR), lacl.get_(sidx));
    }

    @Test
    public void shouldNotUpdateLocalACLOnFailedRemoteUpdate() throws Exception
    {
        SIndex sidx = new SIndex(2);
        when(sidx2sid.get_(eq(sidx))).thenReturn(sid1);
        when(sidx2sid.getNullable_(eq(sidx))).thenReturn(sid1);

        when(spClient.updateACL(any(ByteString.class), anyIterable(PBSubjectRolePair.class)))
                .thenThrow(new ExNoPerm());

        boolean ok = false;
        try {
            aclsync.update_(sidx, ImmutableMap.of(user1, Role.EDITOR));
        } catch (ExNoPerm e) {
            ok = true;
        }
        assertTrue(ok);

        verify(spClient).updateACL(eq(sid1.toPB()), anyIterable(PBSubjectRolePair.class));
        assertTrue(lacl.get_(sidx).isEmpty());
    }

    @Test
    public void shouldDeleteLocalACLOnSuccessfulRemoteDelete() throws Exception
    {
        SIndex sidx = new SIndex(2);
        when(sidx2sid.get_(eq(sidx))).thenReturn(sid1);
        when(sidx2sid.getNullable_(eq(sidx))).thenReturn(sid1);
        lacl.set_(sidx, ImmutableMap.of(user1, Role.EDITOR), t);

        aclsync.delete_(sidx, Collections.singleton(user1));

        verify(spClient).deleteACL(eq(sid1.toPB()), anyIterable(String.class));
        assertTrue(lacl.get_(sidx).isEmpty());
    }

    @Test
    public void shouldNotDeleteLocalACLOnFailedRemoteDelete() throws Exception
    {
        SIndex sidx = new SIndex(2);
        when(sidx2sid.get_(eq(sidx))).thenReturn(sid1);
        when(sidx2sid.getNullable_(eq(sidx))).thenReturn(sid1);
        lacl.set_(sidx, ImmutableMap.of(user1, Role.EDITOR), t);

        when(spClient.deleteACL(any(ByteString.class), anyIterable(String.class)))
                .thenThrow(new ExNoPerm());

        boolean ok = false;
        try {
            aclsync.delete_(sidx, Collections.singleton(user1));
        } catch (ExNoPerm e) {
            ok = true;
        }
        assertTrue(ok);

        verify(spClient).deleteACL(eq(sid1.toPB()), anyIterable(String.class));
        assertEquals(ImmutableMap.of(user1, Role.EDITOR), lacl.get_(sidx));
    }

    @Test
    public void shouldJoinFolderWhenAccessGained() throws Exception
    {
        SIndex sidx = new SIndex(2);
        when(sid2sidx.getNullable_(sid1)).thenReturn(null);
        when(sid2sidx.getAbsent_(sid1, t)).thenReturn(sidx);
        when(stores.getAll_()).thenReturn(Collections.<SIndex>emptySet());

        mockGetSharedFolderNames(sid1, "shared");
        mockGetACL(42L, storeACL(sid1, new SubjectRolePair(user1, Role.OWNER),
                new SubjectRolePair(user2, Role.EDITOR)));

        aclsync.syncToLocal_();

        verify(spClient).getACL(anyLong());
        verify(storeJoiner).joinStore(eq(sidx), eq(sid1), eq("shared"), eq(t));
    }

    @Test
    public void shouldLeaveFolderWhenAccessLost() throws Exception
    {
        SIndex sidx = new SIndex(2);
        when(sidx2sid.get_(eq(sidx))).thenReturn(sid1);
        when(sid2sidx.getNullable_(sid1)).thenReturn(sidx);
        when(stores.getAll_()).thenReturn(ImmutableSet.of(sidx));

        lacl.set_(sidx, ImmutableMap.of(user1, Role.EDITOR), t);
        mockGetACL(42L, storeACL(sid1));

        aclsync.syncToLocal_();

        verify(spClient).getACL(anyLong());
        verify(storeJoiner).leaveStore(sidx, sid1, t);
    }

    @Test
    public void shouldNotJoinFolderWhenAccessUnchanged() throws Exception
    {
        SIndex sidx = new SIndex(2);
        when(sid2sidx.get_(sid1)).thenReturn(sidx);
        when(sid2sidx.getNullable_(sid1)).thenReturn(sidx);
        when(stores.getAll_()).thenReturn(ImmutableSet.of(sidx));
        lacl.set_(sidx, ImmutableMap.of(user1, Role.EDITOR), t);

        mockGetACL(42L, storeACL(sid1, new SubjectRolePair(user1, Role.OWNER),
                new SubjectRolePair(user2, Role.EDITOR)));

        aclsync.syncToLocal_();

        verify(spClient).getACL(anyLong());
        verifyNoMoreInteractions(storeJoiner);
    }

    @Test
    public void shouldNotLeaveFolderWhenAccessUnchanged() throws Exception
    {
        SIndex sidx = new SIndex(2);
        when(sid2sidx.getNullable_(sid1)).thenReturn(null);
        when(sid2sidx.getAbsent_(sid1, t)).thenReturn(sidx);
        when(stores.getAll_()).thenReturn(Collections.<SIndex>emptySet());

        mockGetSharedFolderNames(sid1, "shared");
        mockGetACL(42L, storeACL(sid1, new SubjectRolePair(user2, Role.EDITOR)));

        aclsync.syncToLocal_();

        verify(spClient).getACL(anyLong());
        verifyNoMoreInteractions(storeJoiner);
    }
}
