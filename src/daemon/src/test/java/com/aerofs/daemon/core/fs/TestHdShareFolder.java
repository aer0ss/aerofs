/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.fs;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.daemon.core.polaris.PolarisAsyncClient;
import com.aerofs.daemon.core.polaris.api.LocalChange;
import com.aerofs.daemon.core.polaris.api.LocalChange.Type;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.migration.ImmigrantCreator;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.store.DescendantStores;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.fs.EIShareFolder;
import com.aerofs.daemon.lib.db.UnlinkedRootDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.Path;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgStorageType;
import com.aerofs.lib.ex.ExChildAlreadyShared;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExParentAlreadyShared;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class TestHdShareFolder extends AbstractTest
{
    @Mock Trans t;
    @Mock Token tk;
    @Mock TCB tcb;
    @Mock SPBlockingClient sp;

    @Mock LocalACL acl;
    @Mock TokenManager tokenManager;
    @Mock TransManager tm;
    @Mock ObjectCreator oc;
    @Mock DirectoryService ds;
    @Mock IPhysicalStorage ps;
    @Mock ImmigrantCreator imc;
    @Mock ObjectMover om;
    @Mock ObjectDeleter od;
    @Mock SIDMap sm;
    @Mock StoreHierarchy ss;
    @Mock DescendantStores dss;
    @Mock ACLSynchronizer aclsync;
    @Mock InjectableSPBlockingClientFactory factSP;
    @Mock CfgLocalUser cfgLocalUser;
    @Mock CfgAbsRoots cfgAbsRoots;
    @Mock CfgStorageType cfgStorageType;
    @Mock UnlinkedRootDatabase urdb;
    @Mock PolarisAsyncClient.Factory polarisFactory;
    @Mock PolarisAsyncClient polaris;
    @Mock RemoteLinkDatabase rldb;

    HdShareFolder hd;

    private final UserID localUser = UserID.fromInternal("foo@bar.baz");
    private final SID rootSID = SID.rootSID(localUser);
    private final SID extSID = SID.generate();

    private final UserID user1 = UserID.fromInternal("user1@corp.com");

    @Before
    public void setUp() throws Exception
    {
        AppRoot.set("dummy");

        when(cfgLocalUser.get()).thenReturn(localUser);
        when(tm.begin_()).thenReturn(t);
        when(tokenManager.acquire_(any(Cat.class), anyString())).thenReturn(tk);
        when(tokenManager.acquire_(any(Cat.class), anyString())).thenReturn(tk);
        when(tk.pseudoPause_(anyString())).thenReturn(tcb);
        when(factSP.create()).thenReturn(sp);
        when(sp.signInRemote()).thenReturn(sp);
        when(polarisFactory.create()).thenReturn(polaris);

        when(cfgAbsRoots.getNullable(rootSID)).thenReturn("/AeroFS");
        when(cfgAbsRoots.get(extSID)).thenReturn("/external");
        when(cfgAbsRoots.getNullable(extSID)).thenReturn("/external");
        when(cfgStorageType.get()).thenReturn(StorageType.LINKED);

        CompletableFuture<RemoteLink> completed = new CompletableFuture<>();
        completed.complete(null);
        when(rldb.wait_(any(SIndex.class), any(OID.class))).thenReturn(completed);

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            ((Executor)args[4]).execute(() -> ((AsyncTaskCallback)args[2]).onSuccess_(true));
            return null;
        }).when(polaris).post(anyString(), any(), any(AsyncTaskCallback.class), any(), any(Executor.class));

        MockDS mds = new MockDS(rootSID, ds, sm, sm);
        mds.root()
            .dir("d")
                    .dir("d").parent()
                    .anchor("a").parent().parent()
            .file("f").parent()
            .anchor("a")
                    .dir("d");
        mds.root(extSID)
                .dir("d");

        // TODO: MockDS should handle that ideally
        when(ss.isRoot_(mds.root().soid().sidx())).thenReturn(true);
        when(ss.isRoot_(mds.root(extSID).soid().sidx())).thenReturn(true);
        l.info("{} {}", mds.root().dir("d").soid(), mds.root().dir("d").anchor("a").soid());
        when(dss.getDescendantStores_(mds.root().dir("d").soid()))
                .thenReturn(ImmutableSet.of(mds.root().dir("d").anchor("a").soid().sidx()));

        hd = new HdShareFolder(tokenManager, ds, sm, ss, dss, aclsync, factSP, cfgAbsRoots, urdb,
                rldb, polarisFactory);
    }

    private void handle(Path path, UserID... users) throws Exception
    {
        ImmutableMap.Builder<String, Permissions> roles = new ImmutableMap.Builder<>();
        for (UserID u : users) roles.put(u.getString(), Permissions.EDITOR);
        hd.handleThrows_(new EIShareFolder(path, roles.build(), "", false));
    }

    @SuppressWarnings("unchecked")
    <T> Iterable<T> anyIterableOf(Class<T> c)
    {
        return (Iterable<T>)any(Iterable.class);
    }

    @Test
    public void shouldThrowWhenTryingToShareUserRoot() throws Exception
    {
        try {
            handle(Path.root(rootSID));
            fail();
        } catch (ExNoPerm e) {}

        verifyZeroInteractions(sp);
    }

    @Test
    public void shouldThrowWhenTryingToShareFile() throws Exception
    {
        try {
            handle(Path.fromString(rootSID, "f"));
            fail();
        } catch (ExNotDir e) {}

        verifyZeroInteractions(sp);
    }

    @Test
    public void shouldThrowWhenTryingToShareFolderUnderAnchor() throws Exception
    {
        try {
            handle(Path.fromString(rootSID, "a/d"));
            fail();
        } catch (ExParentAlreadyShared e) {}

        verifyZeroInteractions(sp);
    }

    @Test
    public void shouldThrowWhenTryingToShareFolderAboveAnchor() throws Exception
    {
        try {
            handle(Path.fromString(rootSID, "d"));
            fail();
        } catch (ExChildAlreadyShared e) {}

        verifyZeroInteractions(sp);
    }

    @Test
    public void shouldThrowWhenTryingToShareFolderUnderExternalShare() throws Exception
    {
        try {
            handle(Path.fromString(extSID, "d"));
            fail();
        } catch (ExParentAlreadyShared e) {}

        verifyZeroInteractions(sp);
    }

    @Test
    public void shouldThrowWhenTryingToInviteMembersToInvalidExternalShare() throws Exception
    {
        try {
            handle(Path.root(SID.generate()), user1);
            fail();
        } catch (ExBadArgs e) {}

        verifyZeroInteractions(sp);
    }

    @Test
    public void shouldShare() throws Exception
    {
        IPhysicalFolder pf = mock(IPhysicalFolder.class);
        when(ps.newFolder_(any(ResolvedPath.class))).thenReturn(pf);

        Path path = Path.fromString(rootSID, "d/d");
        SOID soid = ds.resolveThrows_(path);
        handle(path, user1);

        verify(sp).shareFolder(eq("d"), any(ByteString.class),
                anyIterableOf(PBSubjectPermissions.class), anyString(), eq(false),
                any(Boolean.class));
        verify(polaris).post(eq("/objects/" + ds.getOA_(soid).parent().toStringFormal()),
                argThat(new BaseMatcher<LocalChange>() {
            @Override
            public boolean matches(Object o) {
                return o instanceof LocalChange && ((LocalChange) o).type == Type.SHARE
                        && ((LocalChange) o).child.equals(soid.oid().toStringFormal());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("SHARE(").appendValue(soid.oid()).appendText(")");
            }
        }), any(AsyncTaskCallback.class), any(), any(Executor.class));
    }

    @Test
    public void shouldInviteMoreMembers() throws Exception
    {
        handle(Path.fromString(rootSID, "a"), user1);

        verify(sp).shareFolder(eq("a"), any(ByteString.class),
                anyIterableOf(PBSubjectPermissions.class), anyString(), eq(false),
                any(Boolean.class));
    }

    @Test
    public void shouldInviteMoreMembersToExternal() throws Exception
    {
        handle(Path.root(extSID), user1);

        verify(sp).shareFolder(eq("external"), any(ByteString.class),
                anyIterableOf(PBSubjectPermissions.class), anyString(), eq(false),
                any(Boolean.class));
    }

    // TODO: polaris tests
}
