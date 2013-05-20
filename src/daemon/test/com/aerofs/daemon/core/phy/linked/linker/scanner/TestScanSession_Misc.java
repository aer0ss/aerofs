/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.linked.linker.scanner;

import com.aerofs.daemon.core.first_launch.OIDGenerator;
import com.aerofs.daemon.core.phy.linked.linker.IDeletionBuffer;
import com.aerofs.daemon.core.phy.linked.linker.PathCombo;
import com.aerofs.daemon.core.mock.TestUtilCore.ExArbitrary;
import com.aerofs.daemon.core.mock.logical.*;
import com.aerofs.daemon.core.mock.physical.MockPhysicalDir;
import com.aerofs.daemon.core.mock.physical.MockPhysicalFile;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.Util;
import com.aerofs.lib.Path;
import com.aerofs.daemon.core.phy.linked.linker.MightCreate.Result;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.Set;

import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * Miscellanous functionality tests for ScanSession.
 */
public class TestScanSession_Misc extends AbstractTestScanSession
{

    public TestScanSession_Misc()
    {
        super(Util.join("foo", "bar"));
    }

    @Override
    protected MockPhysicalDir createMockPhysicalFileSystem()
    {
        return new MockPhysicalDir("foo",
                new MockPhysicalDir("bar",
                    new MockPhysicalFile("f1"),
                    new MockPhysicalDir("d2",
                        new MockPhysicalFile("f2.1"),
                        new MockPhysicalDir("d2.2"),
                        new MockPhysicalDir("d2.3")
                    ),
                    new MockPhysicalDir("a3")
                )
            );
    }

    @Override
    protected MockRoot createMockLogicalFileSystem()
    {
        return new MockRoot(
                new MockFile("f1", 2),
                new MockDir("d2",
                    new MockFile("f2.1"),
                    new MockDir("d2.2"),
                    new MockDir("d2.3")
                ),
                new MockAnchor("a3", true)  // an expelled anchor
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

                        // pc might be null when the test code redefines mightCreate()'s mocking
                        // behavior.
                        if (pc == null) return null;

                        String path = pc._absPath;
                        return factFile.create(path).isDirectory() ? Result.EXISTING_FOLDER :
                                Result.FILE;
                    }
                });
    }

    @Test
    public void shouldScanAllRootPathsSpecifiedInEvent() throws Exception
    {
        String p1 = Util.join(pRoot, "d2", "d2.2");
        String p2 = Util.join(pRoot, "d2", "d2.3");
        Set<String> paths = ImmutableSet.of(p1, p2);

        for (String p : paths) mockPhysicalDir(p);

        factSS.create_(root, paths, false).scan_();

        verify(factFile.create(p1)).list();
        verify(factFile.create(p2)).list();
    }

    // See Comment (A) in Scanner for explanation on this requirement
    @Test
    public void shouldIgnoreIfRootPathDoesntExistOrIsAFile() throws Exception
    {
        String path = Util.join(pRoot, "d2");

        InjectableFile f = factFile.create(path);
        when(f.isDirectory()).thenReturn(true);
        when(f.canRead()).thenReturn(true)
                .thenReturn(false);

        // the method should not throw
        factSS.create_(root, Collections.singleton(path), false).scan_();
    }

    // See Comment (A) in Scanner for explanation on this requirement
    @Test (expected = Exception.class)
    public void shouldThrowIfSubFolderDisappearedOrBecameAFile() throws Exception
    {
        String path = Util.join(pRoot, "d2");
        String subpath = Util.join(path, "d2.2");

        mockPhysicalDir(path);
        mockPhysicalDir(subpath);

        // The test directory can be read, but list() fails; this should throw.
        when(factFile.create(subpath).list()).thenReturn(null);

        factSS.create_(root, Collections.singleton(path), true).scan_();
    }

    @SuppressWarnings("unchecked")
    @Test (expected = ExArbitrary.class)
    public void shouldThrowIfUnderlyingProcessingThrows() throws Exception
    {
        when(mc.mightCreate_(any(PathCombo.class), any(IDeletionBuffer.class),
                any(OIDGenerator.class), any(Trans.class)))
            .thenThrow(ExArbitrary.class);

        factSS.create_(root, Collections.singleton(pRoot), false).scan_();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldRemoveAllSOIDsIfUnderlyingProcessingThrows() throws Exception
    {
        when(mc.mightCreate_(any(PathCombo.class), any(IDeletionBuffer.class),
                any(OIDGenerator.class), any(Trans.class)))
            .thenThrow(ExArbitrary.class);

        try {
            factSS.create_(root, Collections.singleton(pRoot), false).scan_();
            fail();
        } catch (ExArbitrary e) {
        } finally {
            verify(h, never()).releaseAll_();
            verify(h).removeAll_();
        }
    }

    @Test
    public void shouldNotRecurseResuriveFlagIsFalse() throws Exception
    {
        factSS.create_(root, Collections.singleton(pRoot), false).scan_();

        InjectableFile f = factFile.create(Util.join(pRoot, "d2"));
        verify(f, never()).list();
    }

    @Test
    public void shouldRecurseIntoNewFolderEvenIfRecursiveFlagIsFalse() throws Exception
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

                    if (pc._path.equals(new Path(rootSID, "d2"))) {
                        return Result.NEW_OR_REPLACED_FOLDER;
                    } else {
                        InjectableFile f = factFile.create(pc._absPath);
                        return f.isDirectory() ? Result.EXISTING_FOLDER : Result.FILE;
                    }
                }
            });

        InjectableFile f = factFile.create(Util.join(pRoot, "d2"));
        when(f.isDirectory()).thenReturn(true);
        when(f.canRead()).thenReturn(true);
        factSS.create_(root, Collections.singleton(pRoot), false).scan_();

        verify(f).list();
    }

    /**
     * Tests if the paths "/" and "/d2" will not produce an AssertionError on hold_
     * as we disable remove_. The ScanSession algorithm should detect that "/d2"
     * is a child path and not scan the same path a second time.
     */
    @Test
    public void shouldNotHoldNodesTwiceWithOverlappingPathsAndRecurseTrue()
            throws Exception
    {
        // Act as if none of these folders are available in the database.
        doNothing().when(delBuffer).remove_(any(SOID.class));
        Set<String> paths = ImmutableSet.of(Util.join(pRoot, "d2"), pRoot);

        for (String s : paths) { mockPhysicalDir(s); }

        factSS.create_(root, paths, true).scan_();
        verify(h, times(5)).hold_(any(SOID.class));
    }

    /**
     * Tests the same as above but with recurse set to false however we should still
     * recurse down to "/d2" because it's a NEW_OR_REPLACED_FOLDER.
     */
    @Test
    public void shouldNotHoldNodesTwiceWithOverlappingPathsAndRecurseFalse()
            throws Exception
    {
        // Act as if none of these folders are available in the database.
        doNothing().when(delBuffer).remove_(any(SOID.class));

        when(mc.mightCreate_(any(PathCombo.class), any(IDeletionBuffer.class),
                any(OIDGenerator.class), any(Trans.class)))
            .then(new Answer<Result>()
            {
                @Override
                public Result answer(InvocationOnMock invocation)
                        throws Throwable
                {
                    PathCombo pc = (PathCombo) invocation.getArguments()[0];

                    if (pc._path.equals(new Path(rootSID, "d2"))) {
                        return Result.NEW_OR_REPLACED_FOLDER;
                    } else {
                        InjectableFile f = factFile.create(pc._absPath);
                        return f.isDirectory() ? Result.EXISTING_FOLDER : Result.FILE;
                    }
                }
            });

        Set<String> paths = ImmutableSet.of(pRoot, Util.join(pRoot, "d2"));

        for (String s : paths) { mockPhysicalDir(s); }

        factSS.create_(root, paths, false).scan_();
        verify(h, times(5)).hold_(any(SOID.class));
    }

    @Test
    public void shouldHoldAndReleaseAllSOIDs() throws Exception
    {
        mockPhysicalDir(pRoot);
        mockPhysicalDir(Util.join(pRoot, "d2"));

        factSS.create_(root, Collections.singleton(pRoot), true).scan_();

        // TODO: do not hard-code the expected number of logical objects
        verify(h, times(5)).hold_(any(SOID.class));
        verify(h).releaseAll_();
        verify(h, never()).removeAll_();
    }

    // once upon a time, expelled anchors triggered either assertion failures or crashes
    @Test
    public void shouldNotCrashOnExpelledAnchorAsParent() throws Exception
    {
        factSS.create_(root, Collections.singleton(Util.join(pRoot, "a3")), false).scan_();
    }

    @Test
    public void shouldNotInfiniteLoopIfBatchContainsNonDirs() throws Exception
    {
        assertTrue(factSS.create_(root, Collections.singleton(Util.join(pRoot, "f1")), false).scan_());
    }
}
