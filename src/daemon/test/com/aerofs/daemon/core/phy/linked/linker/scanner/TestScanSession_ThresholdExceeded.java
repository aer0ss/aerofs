/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.linked.linker.scanner;

import com.aerofs.daemon.core.first_launch.OIDGenerator;
import com.aerofs.daemon.core.mock.physical.MockPhysicalTree;
import com.aerofs.daemon.core.phy.linked.linker.IDeletionBuffer;
import com.aerofs.daemon.core.phy.linked.linker.MightCreate.Result;
import com.aerofs.daemon.core.phy.linked.linker.PathCombo;
import com.aerofs.daemon.core.mock.logical.MockDir;
import com.aerofs.daemon.core.mock.logical.MockFile;
import com.aerofs.daemon.core.mock.logical.MockRoot;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Set;

import static com.aerofs.daemon.core.mock.physical.MockPhysicalTree.dir;
import static com.aerofs.daemon.core.mock.physical.MockPhysicalTree.file;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests ScanSession behavior after exceeding the time or updates thresholds.
 */
public class TestScanSession_ThresholdExceeded extends AbstractTestScanSession
{
    private final int NUMBER_OF_FILES = 1001;

    public TestScanSession_ThresholdExceeded()
    {
        super("root");
    }

    @Override
    protected MockPhysicalTree createMockPhysicalFileSystem()
    {
        MockPhysicalTree[] mockedFiles = new MockPhysicalTree[NUMBER_OF_FILES];
        for (int i = 0; i < NUMBER_OF_FILES; i++) {
            mockedFiles[i] = file("f.1." + i);
        }

        return dir(
                "root",
                    dir("dir1", mockedFiles),
                    dir("dir2")
                );
    }

    @Override
    protected MockRoot createMockLogicalFileSystem()
    {
        return new MockRoot(
                new MockDir("dir1", new MockFile("f.1.1")),
                new MockDir("dir2", new MockFile("f.2.1"))
        );
    }

    @Override
    protected void mockMightCreate() throws Exception
    {
        when(mc.mightCreate_(any(PathCombo.class), any(IDeletionBuffer.class),
                any(OIDGenerator.class), any(Trans.class)))
                .then(new Answer<Result>()
                {
                    @Override
                    public Result answer(InvocationOnMock invocation)
                            throws Throwable
                    {
                        PathCombo pc = (PathCombo) invocation.getArguments()[0];

                        // pc might be null when the test code redefines
                        // mightCreate()'s mocking behavior.
                        if (pc == null) return null;

                        String path = pc._absPath;
                        return factFile.create(path).isDirectory() ? Result.EXISTING_FOLDER :
                                Result.FILE;
                    }
                });
    }

    @Test
    public void shouldScanSecondFolderAfterUpdatesThresholdExceeded() throws Exception
    {
        String p1 = Util.join(pRoot, "dir1");
        String p2 = Util.join(pRoot, "dir2");

        Set<String> paths = ImmutableSet.of(p1, p2);
        for (String s : paths) mockPhysicalDir(s);

        ScanSession ss = factSS.create_(root, paths, false);
        assertFalse(ss.scan_());                    // we are not done scanning
        verify(h, times(1)).hold_(any(SOID.class)); // only hold f.1.1
        assertTrue(ss.scan_());
        verify(h, times(2)).hold_(any(SOID.class)); // hold both f.1.1 and f.2.1
    }

}
