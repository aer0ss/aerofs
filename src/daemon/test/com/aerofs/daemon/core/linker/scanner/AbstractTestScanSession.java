/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.linker.scanner;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.first.ScanProgressReporter;
import com.aerofs.daemon.core.linker.MightCreate;
import com.aerofs.daemon.core.linker.TimeoutDeletionBuffer;
import com.aerofs.daemon.core.linker.TimeoutDeletionBuffer.Holder;
import com.aerofs.daemon.core.mock.logical.MockRoot;
import com.aerofs.daemon.core.mock.physical.MockPhysicalDir;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.testlib.AbstractTest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.Before;

import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.when;

public abstract class AbstractTestScanSession extends AbstractTest
{

    @Mock DirectoryService ds;
    @Mock MightCreate mc;
    @Mock TransManager tm;
    @Mock TimeoutDeletionBuffer delBuffer;
    @Mock Holder h;
    @Mock InjectableFile.Factory factFile;
    @Mock CfgAbsRootAnchor cfgAbsRootAnchor;
    @Mock ScanProgressReporter spr;
    @InjectMocks ScanSession.Factory factSS;

    protected final String pRoot;

    public AbstractTestScanSession(String pRoot)
    {
        this.pRoot = pRoot;
    }

    protected abstract MockPhysicalDir createMockPhysicalFileSystem();

    protected abstract MockRoot createMockLogicalFileSystem();

    protected abstract void mockMightCreate() throws Exception;

    @Before
    public void setup() throws Exception
    {
        MockPhysicalDir phyRoot = createMockPhysicalFileSystem();
        MockRoot logicRoot = createMockLogicalFileSystem();

        phyRoot.mock(factFile, null);
        logicRoot.mock(ds, null, null);
        when(tm.begin_()).then(RETURNS_MOCKS);
        when(cfgAbsRootAnchor.get()).thenReturn(pRoot);
        when(delBuffer.newHolder()).thenReturn(h);

        mockMightCreate();
    }
}
