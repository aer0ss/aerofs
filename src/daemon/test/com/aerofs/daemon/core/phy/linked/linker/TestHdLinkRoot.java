/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
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
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.io.File;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Test to make sure that linking an unlink external shared folder works.
 */
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
    @Mock LinkRootUtil lru;
    @Mock CfgLocalUser cfgLocalUser;
    @Mock ACLSynchronizer aclsync;
    @Spy InjectableFile.Factory factFile;
    @Mock InjectableSPBlockingClientFactory factSP;
    @Mock CfgAbsRTRoot cfgAbsRTRoot;

    @InjectMocks HdCreateRoot hdCreateRoot;
    @InjectMocks HdLinkRoot hdLinkRoot;

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
        when(tokenManager.acquire_(any(Cat.class), anyString())).thenReturn(tk);
        when(tk.pseudoPause_(anyString())).thenReturn(tcb);
        when(factSP.create()).thenReturn(sp);
        when(sp.signInRemote()).thenReturn(sp);
        when(cfgAbsRTRoot.get()).thenReturn("");
    }

    private void handle(InjectableFile f, SID sid) throws Exception
    {
        hdLinkRoot.handleThrows_(new EILinkRoot(f.getPath(), sid), Prio.LO);
    }
    @Test
    public void shouldLinkExternalSharedFolder() throws Exception
    {
        SID sid = SID.generate();
        InjectableFile f = factFile.create(path);
        f.mkdir();
        doNothing().when(lru).checkSanity(f);
        handle(f, sid);
        verify(lru).checkSanity(eq(f));
        verify(lru).linkRoot(eq(f), eq(sid));
        verify(lrm, never()).unlink_(any(SID.class), eq(t));
        verifyZeroInteractions(sp, aclsync, sd);
    }
}