/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.fs;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.admin.EILeaveSharedFolder;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.ex.ExNotShared;
import com.aerofs.lib.id.SIndex;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TestHdLeaveSharedFolder extends AbstractTest
{
    @Mock Token tk;
    @Mock TCB tcb;
    @Mock SPBlockingClient sp;

    @Mock TokenManager tokenManager;
    @Mock DirectoryService ds;
    @Mock SIDMap sm;
    @Mock LocalACL lacl;
    @Mock ACLSynchronizer aclsync;
    @Mock InjectableSPBlockingClientFactory factSP;
    @Mock CfgLocalUser cfgLocalUser;

    HdLeaveSharedFolder hd;

    private final UserID localUser = UserID.fromInternal("foo@bar.baz");
    private final SID rootSID = SID.rootSID(localUser);
    private final SID extSID = SID.generate();
    private SID shared;

    @Before
    public void setUp() throws Exception
    {
        AppRoot.set("dummy");

        when(cfgLocalUser.get()).thenReturn(localUser);
        when(tokenManager.acquire_(any(Cat.class), anyString())).thenReturn(tk);
        when(tk.pseudoPause_(anyString())).thenReturn(tcb);
        when(factSP.create()).thenReturn(sp);
        when(sp.signInRemote()).thenReturn(sp);

        when(lacl.check_(any(UserID.class), any(SIndex.class), any(Permissions.class)))
                .thenReturn(true);

        hd = new HdLeaveSharedFolder(tokenManager, ds, aclsync, factSP);

        MockDS mds = new MockDS(rootSID, ds, sm, sm);
        mds.root()
                .dir("d").parent()
                .file("f").parent()
                .anchor("a")
                        .dir("d");
        mds.root(extSID)
                .dir("d");

        shared = SID.anchorOID2storeSID(mds.root().anchor("a").soid().oid());
    }

    private void handle(Path path) throws Exception
    {
        hd.handleThrows_(new EILeaveSharedFolder(path));
    }

    @Test
    public void shouldThrowIfPathPointsToRootAnchor() throws Exception
    {
        try {
            handle(Path.root(rootSID));
            fail();
        } catch (ExNotShared e) {}

        verifyZeroInteractions(sp);
    }

    @Test
    public void shouldThrowIfPathPointsToFile() throws Exception
    {
        try {
            handle(Path.fromString(rootSID, "f"));
            fail();
        } catch (ExNotShared e) {}

        verifyZeroInteractions(sp);
    }

    @Test
    public void shouldThrowIfPathPointsToDir() throws Exception
    {
        try {
            handle(Path.fromString(rootSID, "d"));
            fail();
        } catch (ExNotShared e) {}

        verifyZeroInteractions(sp);
    }

    @Test
    public void shouldThrowIfPathPointsToDirUnderExternalRoot() throws Exception
    {
        try {
            handle(Path.fromString(extSID, "d"));
            fail();
        } catch (ExNotShared e) {}

        verifyZeroInteractions(sp);
    }

    @Test
    public void shouldLeaveAnchor() throws Exception
    {
        handle(Path.fromString(rootSID, "a"));

        verify(sp).leaveSharedFolder(BaseUtil.toPB(shared));
    }

    @Test
    public void shouldLeaveExternalAnchor() throws Exception
    {
        handle(Path.root(extSID));

        verify(sp).leaveSharedFolder(BaseUtil.toPB(extSID));
    }
}
