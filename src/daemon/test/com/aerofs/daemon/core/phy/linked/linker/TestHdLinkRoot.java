/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.mock.TestUtilCore.ExArbitrary;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.fs.EILinkRoot;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsRTRoot;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExChildAlreadyShared;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExParentAlreadyShared;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.testlib.AbstractTest;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import javax.annotation.Nullable;

import java.io.File;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

public class TestHdLinkRoot extends AbstractTest
{
    @Mock Trans t;
    @Mock Token tk;
    @Mock TCB tcb;
    @Mock SPBlockingClient sp;

    @Mock TokenManager tokenManager;
    @Mock StoreCreator sc;
    @Mock StoreDeleter sd;
    @Mock TransManager tm;
    @Mock LinkerRootMap lrm;
    @Mock CfgLocalUser cfgLocalUser;
    @Mock ACLSynchronizer aclsync;
    @Spy InjectableFile.Factory factFile;
    @Mock InjectableSPBlockingClientFactory factSP;
    @Mock CfgAbsRTRoot cfgAbsRTRoot;

    @InjectMocks HdLinkRoot hd;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private String path;
    private static final String name = "foo";

    private final UserID localUser = UserID.fromInternal("foo@bar.baz");

    @Before
    public void setUp() throws Exception
    {
        AppRoot.set("dummy");

        path = new File(Util.join(tempFolder.getRoot().getPath(), name)).getCanonicalPath();

        when(cfgLocalUser.get()).thenReturn(localUser);
        when(tm.begin_()).thenReturn(t);
        when(tokenManager.acquireThrows_(any(Cat.class), anyString())).thenReturn(tk);
        when(tk.pseudoPause_(anyString())).thenReturn(tcb);
        when(factSP.create()).thenReturn(sp);
        when(sp.signInRemote()).thenReturn(sp);
        when(cfgAbsRTRoot.get()).thenReturn("");
    }

    private void handle(String path, @Nullable SID sid) throws Exception
    {
        hd.handleThrows_(new EILinkRoot(path, sid), Prio.LO);
    }

    @Test
    public void shouldThrowIfPathDoesNotExist() throws Exception
    {
        try {
            handle(path, null);
            fail();
        } catch (ExNotFound e) {}
    }

    @Test
    public void shouldThrowIfPathPointsToFile() throws Exception
    {
        factFile.create(path).createNewFile();

        try {
            handle(path, null);
            fail();
        } catch (ExNotDir e) {}
    }

    @Test
    public void shouldThrowIfExistingRootUnderPath() throws Exception
    {
        factFile.create(path).mkdir();
        when(lrm.isAnyRootUnder_(path)).thenReturn(true);

        try {
            handle(path, null);
            fail();
        } catch (ExChildAlreadyShared e) {}
    }

    @Test
    public void shouldThrowIfPathUnderExistingRoot() throws Exception
    {
        factFile.create(path).mkdir();
        when(lrm.isAnyRootUnder_(path)).thenReturn(false);
        when(lrm.rootForAbsPath_(path)).thenReturn(SID.generate());

        try {
            handle(path, null);
            fail();
        } catch (ExParentAlreadyShared e) {}
    }

    @SuppressWarnings("unchecked")
    <T> Iterable<T> anyIterableOf(Class<T> c)
    {
        return (Iterable<T>)any(Iterable.class);
    }

    @Test
    public void shouldCreateSharedFolder() throws Exception
    {
        factFile.create(path).mkdir();
        when(lrm.isAnyRootUnder_(path)).thenReturn(false);
        when(lrm.rootForAbsPath_(path)).thenReturn(null);

        handle(path, null);

        verify(lrm).link_(any(SID.class), eq(path), eq(t));
        verify(sc).createRootStore_(any(SID.class), eq(name), eq(t));

        verify(sp).signInRemote();
        verify(sp).shareFolder(eq(name), any(ByteString.class),
                anyIterableOf(PBSubjectPermissions.class), anyString(), eq(true), any(Boolean.class));
        verify(aclsync).syncToLocal_();

        verify(lrm, never()).unlink_(any(SID.class), eq(t));
        verifyZeroInteractions(sd);
    }

    @Test
    public void shouldUnlinkOnSPException() throws Exception
    {
        factFile.create(path).mkdir();
        when(lrm.isAnyRootUnder_(path)).thenReturn(false);
        when(lrm.rootForAbsPath_(path)).thenReturn(null);
        when(sp.shareFolder(eq(name), any(ByteString.class),
                anyIterableOf(PBSubjectPermissions.class), anyString(), eq(true), any(Boolean.class)))
                .thenThrow(new ExArbitrary());

        try {
            handle(path, null);
            fail();
        } catch (ExArbitrary e) {}

        verify(lrm).link_(any(SID.class), eq(path), eq(t));
        verify(sc).createRootStore_(any(SID.class), eq(name), eq(t));

        verify(sp).signInRemote();
        verify(sp).shareFolder(eq(name), any(ByteString.class),
                anyIterableOf(PBSubjectPermissions.class), anyString(), eq(true), any(Boolean.class));

        verify(lrm).unlink_(any(SID.class), eq(t));
        verify(sd).deleteRootStore_(any(SIndex.class), eq(PhysicalOp.MAP), eq(t));

        verifyZeroInteractions(aclsync);
    }

    @Test
    public void shouldLinkPendingRoot() throws Exception
    {
        SID sid = SID.generate();
        factFile.create(path).mkdir();
        when(lrm.isAnyRootUnder_(path)).thenReturn(false);
        when(lrm.rootForAbsPath_(path)).thenReturn(null);

        handle(path, sid);

        verify(lrm).link_(eq(sid), eq(path), eq(t));
        verify(sc).createRootStore_(eq(sid), eq(name), eq(t));

        verify(lrm, never()).unlink_(any(SID.class), eq(t));
        verifyZeroInteractions(sp, aclsync, sd);
    }
}
