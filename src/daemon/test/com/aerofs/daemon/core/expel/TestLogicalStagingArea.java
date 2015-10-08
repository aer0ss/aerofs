/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.expel;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.IContentVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.LogicalStagingAreaDatabase.StagedFolder;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.protocol.PrefixVersionControl;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.lib.Path;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.InMemoryCoreDBCW;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.verification.VerificationMode;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;


public class TestLogicalStagingArea extends AbstractTest
{
    @Mock DirectoryService ds;
    @Mock IPhysicalStorage ps;
    @Mock IContentVersionControl cvc;
    @Mock PrefixVersionControl pvc;
    @Mock CoreScheduler sched;
    @Mock TransManager tm;
    @Mock SIDMap sm;
    @Mock StoreDeletionOperators sdo;

    @Mock Trans t;

    InjectableDriver dr = new InjectableDriver(OSUtil.get());
    InMemoryCoreDBCW dbcw = new InMemoryCoreDBCW(dr);
    LogicalStagingAreaDatabase lsadb = new LogicalStagingAreaDatabase(dbcw);

    MockDS mds;
    SID rootSID = SID.generate();

    LogicalStagingArea lsa;

    @Before
    public void setUp() throws Exception
    {
        dbcw.init_();

        mds = new MockDS(rootSID, ds, sm, sm);
        lsa = new LogicalStagingArea(ds, ps, cvc, pvc, lsadb, sched, sm, sdo, tm);

        when(tm.begin_()).thenReturn(t);

        doAnswer(invocation -> {
            SOID soid  = (SOID)invocation.getArguments()[0];
            OA oa = ds.getOA_(soid);
            doReturn(null).when(oa).fidNoExpulsionCheck();
            return null;
        }).when(ds).unsetFID_(any(SOID.class), eq(t));

        doAnswer(invocation -> {
            ((AbstractEBSelfHandling)invocation.getArguments()[0]).handle_();
            return null;
        }).when(sched).schedule(any(IEvent.class), anyLong());

        mds.root()
                .dir("empty").parent()
                .anchor("anchor").parent()
                .dir("foo")
                        .file("qux").parent()
                        .dir("bar")
                                .file("baz").parent()
                                .dir("deep").dir("inside").dir("the").file("moria");

    }

    @After
    public void tearDown() throws Exception
    {
        dbcw.fini_();
    }

    private void assertStagingDatabaseContains(ResolvedPath lp) throws SQLException
    {
        assertStagingDatabaseContains(Arrays.asList(lp));
    }

    private void assertStagingDatabaseContains(Iterable<ResolvedPath> lp) throws SQLException
    {
        IDBIterator<StagedFolder> it = lsadb.listEntries_();
        try {
            for (ResolvedPath p : lp) {
                l.info("expect {} {}", p.soid(), p.toStringRelative());
                assertTrue(it.next_());
                StagedFolder sf = it.get_();
                assertEquals(p.soid(), sf.soid);
                assertEquals(p, sf.historyPath);
            }
            assertFalse(it.next_());
        } finally {
            it.close_();
        }
    }

    private void assertStagingDatabaseContains(String... lp) throws SQLException
    {
        assertStagingDatabaseContains(Collections2.transform(Arrays.asList(lp), s -> {
            try {
                return ds.resolve_(ds.resolveThrows_(Path.fromString(rootSID, s)));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }));
    }

    // TODO: test w/ multiple conflict branches
    private void verifyFileCleanup(SOID soid, Path historyPath) throws SQLException, IOException
    {
        // some cleanup skipped when whole store is going down
        int n = sm.getNullable_(soid.sidx()) != null ? 1 : 0;
        VerificationMode mode = times(n);
        verify(ds, mode).unsetFID_(soid, t);
        verify(cvc, mode).fileExpelled_(soid, t);
        verify(ds, mode).deleteCA_(soid, KIndex.MASTER, t);

        verify(ps).scrub_(eq(soid), eq(historyPath), anyString(), eq(t));
        verify(pvc).deleteAllPrefixVersions_(soid, t);
    }

    private void verifyFileCleanup(String relative) throws SQLException, IOException
    {
        ResolvedPath p = path(relative);
        verifyFileCleanup(p.soid(), p);
    }

    private void verifyFileCleanupNoHistory(String relative) throws SQLException, IOException
    {
        ResolvedPath p = path(relative);
        verifyFileCleanup(p.soid(), Path.root(p.sid()));
    }

    private void verifyFolderCleanup(SOID soid, Path historyPath) throws SQLException, IOException
    {
        // FID cleanup skipped when whole store is going down
        int n = sm.getNullable_(soid.sidx()) != null ? 1 : 0;
        verify(ds, times(n)).unsetFID_(soid, t);

        verify(ps).scrub_(eq(soid), eq(historyPath), anyString(), eq(t));
    }

    private void verifyFolderCleanup(String relative) throws SQLException, IOException
    {
        ResolvedPath p = path(relative);
        verifyFolderCleanup(p.soid(), p);
    }

    private void verifyFolderCleanupNoHistory(String relative) throws SQLException, IOException
    {
        ResolvedPath p = path(relative);
        verifyFolderCleanup(p.soid(), Path.root(p.sid()));
    }

    private ResolvedPath path(String relative) throws SQLException
    {
        SOID soid = ds.resolveNullable_(Path.fromString(rootSID, relative));
        return ds.resolve_(checkNotNull(soid));
    }

    @Test
    public void shouldCleanupFileImmediately() throws Exception
    {
        SOID soid = ds.resolveNullable_(Path.fromString(rootSID, "foo/qux"));
        checkNotNull(soid);
        ResolvedPath p = ds.resolve_(soid);

        lsa.stageCleanup_(soid, p, null, t);

        verifyFileCleanup(soid, p);
        assertStagingDatabaseContains();
    }

    @Test
    public void shouldNotStageEmptyFolder() throws Exception
    {
        SOID soid = ds.resolveNullable_(Path.fromString(rootSID, "empty"));
        ResolvedPath p = ds.resolve_(soid);

        lsa.stageCleanup_(soid, p, "", t);

        assertStagingDatabaseContains();
        verify(ps, never()).scrub_(any(SOID.class), any(Path.class), anyString(), any(Trans.class));
    }

    @Test
    public void shouldNotStageAnchor() throws Exception
    {
        SOID soid = ds.resolveNullable_(Path.fromString(rootSID, "anchor"));
        ResolvedPath p = ds.resolve_(soid);

        lsa.stageCleanup_(soid, p, "", t);

        assertStagingDatabaseContains();
        verify(ps, never()).scrub_(any(SOID.class), any(Path.class), anyString(), any(Trans.class));
    }

    @Test
    public void shouldStageFolder() throws Exception
    {
        SOID soid = ds.resolveNullable_(Path.fromString(rootSID, "foo"));
        ResolvedPath p = ds.resolve_(soid);

        lsa.stageCleanup_(soid, p, null, t);

        assertStagingDatabaseContains("foo");
    }

    @Test
    public void shouldCleanupStagedFolder() throws Exception
    {
        SOID soid = ds.resolveNullable_(Path.fromString(rootSID, "foo"));
        ResolvedPath p = ds.resolve_(soid);

        lsadb.addEntry_(soid, p, null, t);

        while (lsa.process_()) {}

        verifyFileCleanup("foo/qux");
        verifyFolderCleanup("foo/bar");
        verifyFileCleanup("foo/bar/baz");
        verifyFolderCleanup("foo/bar/deep");
        verifyFolderCleanup("foo/bar/deep/inside");
        verifyFolderCleanup("foo/bar/deep/inside/the");
        verifyFileCleanup("foo/bar/deep/inside/the/moria");
        assertStagingDatabaseContains();
    }

    @Test
    public void shouldUpdateAliasedStagedFolder() throws Exception
    {
        SOID soid = ds.resolveNullable_(Path.fromString(rootSID, "foo"));
        ResolvedPath p = ds.resolve_(soid);

        lsadb.addEntry_(soid, p, null, t);

        SOID target = new SOID(soid.sidx(), OID.generate());
        lsa.objectAliased_(soid, target, t);

        assertStagingDatabaseContains(p.substituteLastSOID(target));
    }

    @Test
    public void shouldNotAffectStagingWhenMovingUnstaged() throws Exception
    {
        SOID child = ds.resolveNullable_(Path.fromString(rootSID, "foo/bar"));

        // pretend the child was renamed
        lsa.preserveStaging_(ds.resolve_(child), t);

        assertStagingDatabaseContains();
    }

    @Test
    public void shouldNotAffectStagingWhenMovingExplicitlyStaged() throws Exception
    {
        Path parentPath = Path.fromString(rootSID, "foo");
        SOID parent = ds.resolveNullable_(parentPath);

        lsadb.addEntry_(parent, parentPath, null, t);

        // pretend the child was renamed
        lsa.preserveStaging_(ds.resolve_(parent), t);

        assertStagingDatabaseContains("foo");
    }

    @Test
    public void shouldExplictlyStageWhenMovingImplicitlyStaged() throws Exception
    {
        Path parentPath = Path.fromString(rootSID, "foo");
        SOID parent = ds.resolveNullable_(parentPath);

        SOID child = ds.resolveNullable_(Path.fromString(rootSID, "foo/bar"));

        lsadb.addEntry_(parent, parentPath, null, t);

        // pretend the child was renamed
        lsa.preserveStaging_(ds.resolve_(child), t);

        assertStagingDatabaseContains("foo", "foo/bar");
    }

    @Test
    public void shouldPerformSynchronousRootCleanup() throws Exception
    {
        Path p = Path.fromString(rootSID, "foo");
        OA oa = ds.getOA_(ds.resolveThrows_(p));

        lsadb.addEntry_(oa.soid(), p, null, t);

        lsa.ensureClean_(ds.resolve_(oa), t);

        verifyFolderCleanup("foo");
        assertStagingDatabaseContains("foo");
    }

    @Test
    public void shouldPerformSynchronousFolderCleanup() throws Exception
    {
        Path p = Path.fromString(rootSID, "foo");
        OA oa = ds.getOA_(ds.resolveThrows_(p));

        lsadb.addEntry_(oa.soid(), p, null, t);

        lsa.ensureClean_(ds.resolve_(ds.resolveThrows_(Path.fromString(rootSID, "foo/bar"))), t);

        verifyFolderCleanup("foo/bar");
        assertStagingDatabaseContains("foo");
    }

    @Test
    public void shouldPerformSynchronousFileCleanup() throws Exception
    {
        Path p = Path.fromString(rootSID, "foo");
        OA oa = ds.getOA_(ds.resolveThrows_(p));

        lsadb.addEntry_(oa.soid(), p, null, t);

        lsa.ensureClean_(ds.resolve_(ds.resolveThrows_(Path.fromString(rootSID, "foo/qux"))), t);

        verifyFileCleanup("foo/qux");
        assertStagingDatabaseContains("foo");
    }

    @Test
    public void shouldPerformSynchronousStoreCleanup() throws Exception
    {
        Path p = Path.fromString(rootSID, "foo");
        SOID soid = ds.resolveThrows_(p);

        lsadb.addEntry_(soid, p, null, t);

        // pretend the store was deleted and partially cleaned
        when(sm.getNullable_(soid.sidx())).thenReturn(null);

        lsa.ensureStoreClean_(soid.sidx(), t);

        verifyFolderCleanup("foo");
        verifyFileCleanup("foo/qux");
        verifyFolderCleanup("foo/bar");
        verifyFileCleanup("foo/bar/baz");
        verifyFolderCleanup("foo/bar/deep");
        verifyFolderCleanup("foo/bar/deep/inside");
        verifyFolderCleanup("foo/bar/deep/inside/the");
        verifyFileCleanup("foo/bar/deep/inside/the/moria");
        verify(sdo).runAllDeferred_(soid.sidx(), t);
        assertStagingDatabaseContains();
    }

    @Test
    public void shouldCleanEntireStore() throws Exception
    {
        SOID soid = ds.resolveThrows_(Path.root(rootSID));

        // pretend the store was deleted
        when(sm.getNullable_(soid.sidx())).thenReturn(null);
        // empty path: don't keep history
        lsadb.addEntry_(soid, Path.root(rootSID), null, t);

        while (lsa.process_()) {}

        verifyFolderCleanupNoHistory("empty");
        verifyFileCleanupNoHistory("foo/qux");
        verifyFolderCleanupNoHistory("foo/bar");
        verifyFileCleanupNoHistory("foo/bar/baz");
        verifyFolderCleanupNoHistory("foo/bar/deep");
        verifyFolderCleanupNoHistory("foo/bar/deep/inside");
        verifyFolderCleanupNoHistory("foo/bar/deep/inside/the");
        verifyFileCleanupNoHistory("foo/bar/deep/inside/the/moria");
        verify(sdo).runAllDeferred_(soid.sidx(), t);
        assertStagingDatabaseContains();
    }

    @Test
    public void shouldRetryUntilCleanupComplete() throws Exception
    {
        Path path = Path.fromString(rootSID, "foo/bar");
        SOID soid = ds.resolveNullable_(path);

        lsadb.addEntry_(soid, Path.root(rootSID), null, t);

        // for every object, fail the first call to scrub_
        doAnswer(invocation -> {
            doNothing().when(ps).scrub_((SOID)invocation.getArguments()[0],
                    (Path)invocation.getArguments()[1],
                    (String)invocation.getArguments()[2],
                    (Trans)invocation.getArguments()[3]);
            throw new IOException("" + invocation.getArguments()[0]);
        }).when(ps).scrub_(any(SOID.class), any(Path.class), anyString(), eq(t));

        lsa.start_();

        // retries mess with the call counts, hence the simpler verifications
        for (String s : ImmutableSet.of("foo/bar/baz", "foo/bar/deep", "foo/bar/deep/inside",
                "foo/bar/deep/inside/the", "foo/bar/deep/inside/the/moria")) {
            SOID obj = ds.resolveThrows_(Path.fromString(rootSID, s));
            verify(ds).unsetFID_(obj, t);
            verify(ps, atLeast(2)).scrub_(eq(obj), eq(Path.root(rootSID)), anyString(), eq(t));
        }
        assertStagingDatabaseContains();
    }
}
