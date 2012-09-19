package com.aerofs.daemon.core.linker.scanner;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.linker.IDeletionBuffer;
import com.aerofs.daemon.core.linker.MightCreate;
import com.aerofs.daemon.core.linker.PathCombo;
import com.aerofs.daemon.core.linker.TimeoutDeletionBuffer;
import com.aerofs.daemon.core.mock.TestUtilCore.ExArbitrary;
import com.aerofs.daemon.core.mock.logical.*;
import com.aerofs.daemon.core.mock.physical.MockPhysicalDir;
import com.aerofs.daemon.core.mock.physical.MockPhysicalFile;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.Util;
import com.aerofs.lib.Path;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.daemon.core.linker.MightCreate.Result;
import com.aerofs.daemon.core.linker.TimeoutDeletionBuffer.Holder;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.HashSet;
import static org.mockito.Mockito.*;

public class TestScanSession extends AbstractTest
{
    @Mock DirectoryService ds;
    @Mock MightCreate mc;
    @Mock TransManager tm;
    @Mock TimeoutDeletionBuffer delBuffer;
    @Mock Holder h;
    @Mock InjectableFile.Factory factFile;
    @Mock CfgAbsRootAnchor cfgAbsRootAnchor;

    @InjectMocks ScanSession.Factory factSS;

    MockPhysicalDir phyRoot =
            new MockPhysicalDir("foo",
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

    MockRoot logicRoot =
            new MockRoot(
                new MockFile("f1", 2),
                new MockDir("d2",
                    new MockFile("f2.1"),
                    new MockDir("d2.2"),
                    new MockDir("d2.3")
                ),
                new MockAnchor("a3", true)  // an expelled anchor
            );

    private final static String pRoot = Util.join("foo", "bar");

    @Before
    public void setup() throws Exception
    {
        phyRoot.mock(factFile, null);
        logicRoot.mock(ds, null, null, null, null, null);

        when(tm.begin_()).then(RETURNS_MOCKS);
        when(cfgAbsRootAnchor.get()).thenReturn(pRoot);
        when(delBuffer.newHolder()).thenReturn(h);

        when(mc.mightCreate_(any(PathCombo.class), any(IDeletionBuffer.class), any(Trans.class)))
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
        HashSet<String> paths = new HashSet<String>();
        paths.add(p1);
        paths.add(p2);
        factSS.create_(paths, false).scan_();

        verify(factFile.create(p1)).list();
        verify(factFile.create(p2)).list();
    }

    // See Comment (A) in Scanner for explanation on this requirement
    @Test
    public void shouldIgnoreIfRootPathDoesntExistOrIsAFile() throws Exception
    {
        String path = Util.join(pRoot, "d2");
        InjectableFile f = factFile.create(path);
        when(f.isDirectory()).thenReturn(false);

        // the method should not throw
        factSS.create_(Collections.singleton(path), false).scan_();
    }

    // See Comment (A) in Scanner for explanation on this requirement
    @Test (expected = Exception.class)
    public void shouldThrowIfSubFolderDisappearedOrBecameAFile() throws Exception
    {
        String path = Util.join(pRoot, "d2");
        String subpath = Util.join(path, "d2.2");
        InjectableFile f = factFile.create(subpath);
        when(f.list()).thenReturn(null);

        factSS.create_(Collections.singleton(path), true).scan_();
    }

    @SuppressWarnings("unchecked")
    @Test (expected = ExArbitrary.class)
    public void shouldThrowIfUnderlyingProcessingThrows() throws Exception
    {
        when(mc.mightCreate_(any(PathCombo.class), any(IDeletionBuffer.class), any(Trans.class)))
            .thenThrow(ExArbitrary.class);

        factSS.create_(Collections.singleton(pRoot), false).scan_();
    }

    @SuppressWarnings("unchecked")
    public void shouldRemoveAllSOIDsIfUnderlyingProcessingThrows() throws Exception
    {
        when(mc.mightCreate_(any(PathCombo.class), any(IDeletionBuffer.class), any(Trans.class)))
            .thenThrow(ExArbitrary.class);

        try {
            factSS.create_(Collections.singleton(pRoot), false).scan_();
        } finally {
            verify(h, never()).releaseAll_();
            verify(h).removeAll_();
        }
    }

    @Test
    public void shouldNotRecurseResuriveFlagIsFalse() throws Exception
    {
        factSS.create_(Collections.singleton(pRoot), false).scan_();

        InjectableFile f = factFile.create(Util.join(pRoot, "d2"));
        verify(f, never()).list();
    }

    @Test
    public void shouldRecurseIntoNewFolderEvenIfRecursiveFlagIsFalse() throws Exception
    {
        when(mc.mightCreate_(any(PathCombo.class), any(IDeletionBuffer.class), any(Trans.class)))
            .then(new Answer<Result>()
            {
                @Override
                public Result answer(InvocationOnMock invocation)
                        throws Throwable
                {
                    PathCombo pc = (PathCombo) invocation.getArguments()[0];

                    if (pc._path.equals(new Path("d2"))) {
                        return Result.NEW_OR_REPLACED_FOLDER;
                    } else {
                        InjectableFile f = factFile.create(pc._absPath);
                        return f.isDirectory() ? Result.EXISTING_FOLDER : Result.FILE;
                    }
                }
            });

        factSS.create_(Collections.singleton(pRoot), false).scan_();

        InjectableFile f = factFile.create(Util.join(pRoot, "d2"));
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

        String p = Util.join(pRoot, "d2");
        HashSet<String> paths = Sets.newHashSet();
        paths.add(p);
        paths.add(pRoot);

        factSS.create_(paths, true).scan_();
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

        when(mc.mightCreate_(any(PathCombo.class), any(IDeletionBuffer.class), any(Trans.class)))
            .then(new Answer<Result>()
            {
                @Override
                public Result answer(InvocationOnMock invocation)
                        throws Throwable
                {
                    PathCombo pc = (PathCombo) invocation.getArguments()[0];

                    if (pc._path.equals(new Path("d2"))) {
                        return Result.NEW_OR_REPLACED_FOLDER;
                    } else {
                        InjectableFile f = factFile.create(pc._absPath);
                        return f.isDirectory() ? Result.EXISTING_FOLDER : Result.FILE;
                    }
                }
            });

        HashSet<String> paths = Sets.newHashSet();
        paths.add(pRoot);
        paths.add(Util.join(pRoot, "d2"));

        factSS.create_(paths, false).scan_();
        verify(h, times(5)).hold_(any(SOID.class));
    }

    @Test
    public void shouldHoldAndReleaseAllSOIDs() throws Exception
    {
        factSS.create_(Collections.singleton(pRoot), true).scan_();

        // TODO: do not hard-code the expected number of logical objects
        verify(h, times(5)).hold_(any(SOID.class));
        verify(h).releaseAll_();
        verify(h, never()).removeAll_();
    }

    // once upon a time, expelled anchors triggered either assertion failures or crashes
    @Test
    public void shouldNotCrashOnExpelledAnchorAsParent() throws Exception
    {
        factSS.create_(Collections.singleton(Util.join(pRoot, "a3")), false).scan_();
    }
}
