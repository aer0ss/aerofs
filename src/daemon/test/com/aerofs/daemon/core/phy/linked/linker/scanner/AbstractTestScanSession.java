/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.linked.linker.scanner;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.first_launch.ScanProgressReporter;
import com.aerofs.daemon.core.mock.physical.MockPhysicalTree;
import com.aerofs.daemon.core.phy.linked.RepresentabilityHelper;
import com.aerofs.daemon.core.phy.linked.SharedFolderTagFileAndIcon;
import com.aerofs.daemon.core.phy.linked.db.NRODatabase;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.daemon.core.phy.linked.linker.ILinkerFilter;
import com.aerofs.daemon.core.phy.linked.linker.MightCreate;
import com.aerofs.daemon.core.phy.linked.linker.TimeoutDeletionBuffer;
import com.aerofs.daemon.core.phy.linked.linker.TimeoutDeletionBuffer.Holder;
import com.aerofs.daemon.core.mock.logical.MockRoot;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtilLinux;
import com.aerofs.testlib.AbstractTest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.Before;
import org.mockito.Spy;

import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.when;

public abstract class AbstractTestScanSession extends AbstractTest
{
    @Spy IOSUtil os = new OSUtilLinux();
    @Mock NRODatabase nro;
    @Mock DirectoryService ds;
    @Mock MightCreate mc;
    @Mock TransManager tm;
    @Mock TimeoutDeletionBuffer delBuffer;
    @Mock Holder h;
    @Mock InjectableFile.Factory factFile;
    @Mock ScanProgressReporter spr;
    @Mock ILinkerFilter filter;
    @Mock RepresentabilityHelper rh;
    @Mock SharedFolderTagFileAndIcon sfti;
    @InjectMocks ScanSession.Factory factSS;

    protected final String pRoot;
    final SID rootSID = SID.generate();
    @Mock LinkerRoot root;

    public AbstractTestScanSession(String pRoot)
    {
        this.pRoot = pRoot;
    }

    protected abstract MockPhysicalTree createMockPhysicalFileSystem();

    protected abstract MockRoot createMockLogicalFileSystem();

    protected abstract void mockMightCreate() throws Exception;

    @Before
    public void setup() throws Exception
    {
        MockPhysicalTree phyRoot = createMockPhysicalFileSystem();
        MockRoot logicRoot = createMockLogicalFileSystem();


        phyRoot.mock(factFile, null);
        logicRoot.mock(rootSID, ds, null, null);
        when(tm.begin_()).then(RETURNS_MOCKS);
        when(delBuffer.newHolder()).thenReturn(h);

        when(root.sid()).thenReturn(rootSID);
        when(root.absRootAnchor()).thenReturn(pRoot);

        // physical root needs to appear readable
        when(factFile.create(pRoot).canRead()).thenReturn(true);
        when(factFile.create(pRoot).isDirectory()).thenReturn(true);

        mockMightCreate();
    }

    protected void mockPhysicalDir(String path)
    {
        InjectableFile f = factFile.create(path);
        when(f.isDirectory()).thenReturn(true);
        when(f.canRead()).thenReturn(true);
    }
}
