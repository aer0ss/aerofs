/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsRTRoot;
import com.aerofs.lib.ex.ExChildAlreadyShared;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExParentAlreadyShared;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.io.File;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestLinkRootUtil extends AbstractTest
{
    @Mock Trans t;

    @Mock StoreCreator sc;
    @Mock TransManager tm;
    @Mock LinkerRootMap lrm;
    @Mock CfgAbsRTRoot cfgAbsRTRoot;
    @Spy InjectableFile.Factory factFile;

    @InjectMocks LinkRootUtil lru;

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

        when(tm.begin_()).thenReturn(t);
        when(cfgAbsRTRoot.get()).thenReturn("");
    }

    @Test
    public void shouldThrowIfPathPointsToFile() throws Exception
    {
        InjectableFile f = factFile.create(path);
        f.createNewFile();

        try {
            lru.checkSanity(f);
            fail();
        } catch (ExNotDir e) {}
    }

    @Test
    public void shouldThrowIfExistingRootUnderPath() throws Exception
    {
        InjectableFile f = factFile.create(path);
        f.mkdir();
        when(lrm.isAnyRootUnder_(path)).thenReturn(true);
        try {
            lru.checkSanity(f);
            fail();
        } catch (ExChildAlreadyShared e) {}
    }

    @Test
    public void shouldThrowIfPathUnderExistingRoot() throws Exception
    {
        InjectableFile f = factFile.create(path);
        f.mkdir();
        when(lrm.isAnyRootUnder_(path)).thenReturn(false);
        when(lrm.rootForAbsPath_(path)).thenReturn(SID.generate());
        try {
            lru.checkSanity(f);
            fail();
        } catch (ExParentAlreadyShared e) {}
    }

    @Test
    public void shouldLinkExternalSharedFolder() throws Exception
    {
        SID sid = SID.generate();
        InjectableFile f = factFile.create(path);
        f.mkdir();
        lru.linkRoot(f, sid);
        verify(lrm).link_(eq(sid), eq(path), eq(t));
        verify(sc).createRootStore_(eq(sid), eq(name), eq(t));
    }
}