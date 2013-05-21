/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.syncstatus;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.mock.logical.IsSOIDAtPath;
import com.aerofs.daemon.core.mock.logical.LogicalObjectsPrinter;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.lib.db.ITransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.CounterVector;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgAggressiveChecking;
import com.aerofs.lib.id.SOID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.testlib.AbstractTest;
import com.beust.jcommander.internal.Lists;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.invocation.InvocationMatcher;
import org.mockito.internal.verification.api.VerificationData;
import org.mockito.invocation.Invocation;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.verification.VerificationMode;

import java.sql.SQLException;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for aggregate sync status
 *
 * Each test exercises a specific update scenario in reaction to a modification of the (mocked)
 * logical object hierarchy (see {@link MockDS}).
 *
 * All tests follow a common pattern:
 *   1. setup initial state of the mocked object hierarchy
 *   2. trigger a state change through one of the helper methods of MockDS
 *   3. verify the sequence of setAggregateSyncStatus calls made by AggregateSyncStatus
 *
 * For extra strictness, there are also assertions to test the way aggregate sync status bitvectors
 * are derived from aggregate sync status counters.
 */
public class TestAggregateSyncStatus extends AbstractTest
{
    @Mock Trans t;
    @Mock DirectoryService ds;
    @Mock SIDMap sm;
    @Mock MapSIndex2DeviceBitMap sidx2dbm;
    @Mock CfgAggressiveChecking config;

    MockDS mds;
    @InjectMocks AggregateSyncStatus agsync;

    final SID rootSID = SID.generate();

    /**
     * A couple of remote device IDs to test sync status
     */
    final DID d0 = new DID(UniqueID.generate());
    final DID d1 = new DID(UniqueID.generate());
    final DID d2 = new DID(UniqueID.generate());

    void assertAggregateSyncStatusEquals(String path, int... counters) throws Exception
    {
        SOID soid = ds.resolveThrows_(Path.fromString(rootSID, path));
        OA oa = ds.getOANullable_(soid);
        if (oa.isAnchor()) soid = ds.followAnchorNullable_(oa);
        l.info("{} {}", soid, path);
        Assert.assertEquals(new CounterVector(counters), ds.getAggregateSyncStatus_(soid));
    }


    void assertAggregateSyncStatusVectorEquals(String path, boolean... status) throws Exception
    {
        SOID soid = ds.resolveThrows_(Path.fromString(rootSID, path));
        l.info("{} {}", soid, path);
        Assert.assertEquals(new BitVector(status), agsync.getAggregateSyncStatusVector_(soid));
    }

    @Before
    public void setup() throws Exception
    {
        // Enable aggressive consistency checking.
        when(config.get()).thenReturn(true);

        // need to
        final List<ITransListener> listeners = Lists.newArrayList();

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable
            {
                listeners.add((ITransListener)invocation.getArguments()[0]);
                return null;
            }
        }).when(t).addListener_(any(ITransListener.class));

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable
            {
                l.info("commit");
                for (ITransListener l : listeners) l.committing_(t);
                return null;
            }
        }).when(t).commit_();

        // stub device list of root store
        mds = new MockDS(rootSID, ds, sm, sm, sidx2dbm);
        mds.dids(d0, d1, d2);
    }

    @After
    public void cleanup() throws Exception
    {
        // All test should specify exhaustively the expected aggregateSyncStat updates
        //Assert.assertEquals(expectedAggregateSyncStat, dsVerificationMode.invocationCount());
    }

    @Test
    public void shouldUpdateOnFileCreation() throws Exception
    {
        // initial state
        mds.root().agss(1, 0, 0)
                .dir("foo").ss(true, true, true).agss(2, 1, 1)
                        .dir("bar").ss(true, true, true).agss(2, 1, 2)
                                .file("hello").ss(true, true, true).parent()
                                .file("world").ss(true, false, true).parent().parent()
                        .dir("baz").ss(true, true, false).agss(0, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/baz", true, true, true);
        assertAggregateSyncStatusVectorEquals("foo", true, false, false);
        assertAggregateSyncStatusVectorEquals("", true, false, false);

        // state change
        mds.touch("foo/baz/touch", t, agsync);
        t.commit_();

        // verify expected interactions caused by state change
        assertAggregateSyncStatusEquals("foo", 1, 0, 1);
        assertAggregateSyncStatusEquals("", 0, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/baz", false, false, false);
        assertAggregateSyncStatusVectorEquals("foo", false, false, false);
        assertAggregateSyncStatusVectorEquals("", false, false, false);
    }

    @Test
    public void shouldUpdateOnFolderCreation() throws Exception
    {
        // initial state
        mds.root().agss(1, 0, 0)
                .dir("foo").ss(true, true, true).agss(2, 1, 1)
                        .dir("bar").ss(true, true, true).agss(2, 1, 2)
                                .file("hello").ss(true, false, true).parent()
                                .file("world").ss(true, true, true).parent().parent()
                        .dir("baz").ss(true, true, false).agss(0, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/baz", true, true, true);
        assertAggregateSyncStatusVectorEquals("foo", true, false, false);
        assertAggregateSyncStatusVectorEquals("", true, false, false);

        // state change
        mds.mkdir("foo/baz/newfolder", t, agsync);
        t.commit_();

        // verify expected interactions caused by state change
        assertAggregateSyncStatusEquals("foo", 1, 0, 1);
        assertAggregateSyncStatusEquals("", 0, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/baz/newfolder", true, true, true);
        assertAggregateSyncStatusVectorEquals("foo/baz", false, false, false);
        assertAggregateSyncStatusVectorEquals("foo", false, false, false);
        assertAggregateSyncStatusVectorEquals("", false, false, false);
    }

    @Test
    public void shouldIgnoreCreationOfExpelledFile() throws Exception
    {
        // initial state
        mds.root().agss(1, 1, 0)
                .dir("foo").ss(true, true, false).agss(0, 0, 0).parent()
                .dir("baz", true).ss(true, false, true).agss(0, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo", true, true, true);
        assertAggregateSyncStatusVectorEquals("", true, true, false);

        // state change
        mds.touch("baz/touch", t, agsync);
        t.commit_();

        // verify expected interactions caused by state change

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo", true, true, true);
        assertAggregateSyncStatusVectorEquals("", true, true, false);
    }

    @Test
    public void shouldIgnoreDeletionOfExpelledFolder() throws Exception
    {
        // initial state
        mds.root().agss(1, 1, 0)
                .dir("foo").ss(true, true, false).agss(0, 0, 0).parent()
                .dir("baz", true).ss(true, false, true).agss(0, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo", true, true, true);
        assertAggregateSyncStatusVectorEquals("", true, true, false);

        // state change
        mds.delete("baz", t, agsync);
        t.commit_();

        // verify expected interactions caused by state change

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo", true, true, true);
        assertAggregateSyncStatusVectorEquals("", true, true, false);
    }

    @Test
    public void shouldUpdateOnFileDeletion() throws Exception
    {
        // initial state
        mds.root().agss(1, 0, 0)
                .dir("foo").ss(true, true, true).agss(2, 1, 1)
                        .dir("bar").ss(true, true, true).agss(2, 1, 1)
                                .file("hello").ss(true, true, false).parent()
                                .file("world").ss(true, false, true).parent().parent()
                        .dir("baz").ss(true, true, true).agss(0, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar", true, false, false);
        assertAggregateSyncStatusVectorEquals("foo", true, false, false);
        assertAggregateSyncStatusVectorEquals("", true, false, false);

        // state change
        mds.delete("foo/bar/world", t, agsync);
        t.commit_();

        // verify expected interactions caused by state change
        assertAggregateSyncStatusEquals("foo/bar", 1, 1, 0);
        assertAggregateSyncStatusEquals("foo", 2, 2, 1);
        assertAggregateSyncStatusEquals("", 1, 1, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar", true, true, false);
        assertAggregateSyncStatusVectorEquals("foo", true, true, false);
        assertAggregateSyncStatusVectorEquals("", true, true, false);
    }

    @Test
    public void shouldUpdateOnFolderDeletion() throws Exception
    {
        // initial state
        mds.root().agss(1, 0, 0)
                .dir("foo").ss(true, true, true).agss(2, 1, 1)
                        .dir("bar").ss(true, true, false).agss(2, 2, 2)
                                .file("hello").ss(true, true, true).parent()
                                .file("world").ss(true, true, true).parent().parent()
                        .dir("baz").ss(true, false, true).agss(0, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/baz", true, true, true);
        assertAggregateSyncStatusVectorEquals("foo", true, false, false);
        assertAggregateSyncStatusVectorEquals("", true, false, false);

        // state change
        mds.delete("foo/baz", t, agsync);
        t.commit_();

        // verify expected interactions caused by state change
        assertAggregateSyncStatusEquals("foo", 1, 1, 0);
        assertAggregateSyncStatusEquals("", 1, 1, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo", true, true, false);
        assertAggregateSyncStatusVectorEquals("", true, true, false);
    }

    @Test
    public void shouldUpdateOnFileMove() throws Exception
    {
        // initial state
        mds.root().agss(1, 0, 0)
                .dir("foo").ss(true, true, true).agss(2, 1, 1)
                        .dir("bar").ss(true, true, true).agss(2, 1, 1)
                                .file("hello").ss(true, true, false).parent()
                                .file("world").ss(true, false, true).parent().parent()
                        .dir("baz").ss(true, true, true).agss(0, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar", true, false, false);
        assertAggregateSyncStatusVectorEquals("foo", true, false, false);
        assertAggregateSyncStatusVectorEquals("", true, false, false);

        // state change
        mds.move("foo/bar/world", "foo/baz/world", t, agsync);
        t.commit_();

        // verify expected interactions caused by state change
        assertAggregateSyncStatusEquals("foo/bar", 1, 1, 0);
        assertAggregateSyncStatusEquals("foo/baz", 1, 0, 1);
        assertAggregateSyncStatusEquals("foo", 2, 1, 1);
        assertAggregateSyncStatusEquals("", 1, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar", true, true, false);
        assertAggregateSyncStatusVectorEquals("foo/baz", true, false, true);
        assertAggregateSyncStatusVectorEquals("foo", true, false, false);
        assertAggregateSyncStatusVectorEquals("", true, false, false);
    }

    @Test
    public void shouldUpdateOnFolderMove() throws Exception
    {
        // initial state
        mds.root().agss(1, 0, 0)
                .dir("foo").ss(true, true, true).agss(2, 1, 1)
                        .dir("bar").ss(true, true, false).agss(2, 2, 2)
                                .file("hello").ss(true, true, true).parent()
                                .file("world").ss(true, true, true).parent().parent()
                        .dir("baz").ss(true, false, true).agss(0, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/baz", true, true, true);
        assertAggregateSyncStatusVectorEquals("foo", true, false, false);
        assertAggregateSyncStatusVectorEquals("", true, false, false);

        // state change
        mds.move("foo/baz", "foo/bar/baz", t, agsync);
        t.commit_();

        // verify expected interactions caused by state change
        assertAggregateSyncStatusEquals("foo/bar", 3, 2, 3);
        assertAggregateSyncStatusEquals("foo", 1, 0, 0);
        assertAggregateSyncStatusEquals("", 1, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar/baz", true, true, true);
        assertAggregateSyncStatusVectorEquals("foo/bar", true, false, true);
        assertAggregateSyncStatusVectorEquals("foo", true, false, false);
        assertAggregateSyncStatusVectorEquals("", true, false, false);
    }

    @Test
    public void shouldUpdateOnFileSyncStatChange() throws Exception
    {
        // initial state
        mds.root().agss(1, 0, 0)
                .dir("foo").ss(true, true, true).agss(2, 1, 1)
                        .dir("bar").ss(true, true, true).agss(2, 1, 1)
                                .file("hello").ss(true, false, true).parent()
                                .file("world").ss(true, true, false).parent().parent()
                        .dir("baz").ss(true, true, true).agss(0, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar", true, false, false);
        assertAggregateSyncStatusVectorEquals("foo", true, false, false);
        assertAggregateSyncStatusVectorEquals("", true, false, false);

        // state change
        mds.sync("foo/bar/world", new BitVector(false, true, true), t, agsync);
        t.commit_();

        // verify expected interactions caused by state change
        assertAggregateSyncStatusEquals("foo/bar", 1, 1, 2);
        assertAggregateSyncStatusEquals("foo", 1, 1, 2);
        assertAggregateSyncStatusEquals("", 0, 0, 1);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar", false, false, true);
        assertAggregateSyncStatusVectorEquals("foo", false, false, true);
        assertAggregateSyncStatusVectorEquals("", false, false, true);
    }

    @Test
    public void shouldUpdateOnFolderSyncStatChange() throws Exception
    {
        // initial state
        mds.root().agss(1, 1, 0)
                .dir("foo").ss(true, true, true).agss(2, 2, 1)
                        .dir("bar").ss(true, true, true).agss(2, 2, 1)
                                .file("hello").ss(true, true, true).parent()
                                .file("world").ss(true, true, false).parent().parent()
                        .dir("baz").ss(true, true, true).agss(0, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar", true, true, false);
        assertAggregateSyncStatusVectorEquals("foo", true, true, false);
        assertAggregateSyncStatusVectorEquals("", true, true, false);

        // state change
        mds.sync("foo/bar", new BitVector(true, false, false), t, agsync);
        t.commit_();

        // verify expected interactions caused by state change
        assertAggregateSyncStatusEquals("foo", 2, 1, 1);
        assertAggregateSyncStatusEquals("", 1, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar", true, true, false);
        assertAggregateSyncStatusVectorEquals("foo", true, false, false);
        assertAggregateSyncStatusVectorEquals("", true, false, false);
    }

    @Test
    public void shouldStopUpdateAtStoreBoundary() throws Exception
    {
        // initial state
        mds.root().agss(1, 1, 1)
                .dir("foo").ss(true, true, true).agss(1, 1, 1)
                    .anchor("bar").dids(d0, d1, d2).ss(true, true, true).agss(1, 0, 0)
                        .dir("baz").ss(true, true, true).agss(2, 1, 0)
                                .file("hello").ss(true, false, false).parent()
                                .file("world").ss(true, true, false);

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar/baz", true, false, false);
        assertAggregateSyncStatusVectorEquals("foo/bar", true, true, true); // ss:anchor / agss:root
        assertAggregateSyncStatusVectorEquals("foo", true, true, true);
        assertAggregateSyncStatusVectorEquals("", true, true, true);

        // state change
        mds.sync("foo/bar/baz/hello", new BitVector(false, true, true), t, agsync);
        t.commit_();

        // verify expected interactions caused by state change
        assertAggregateSyncStatusEquals("foo/bar/baz", 1, 2, 1);
        assertAggregateSyncStatusEquals("foo/bar", 0, 1, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar/baz", false, true, false);
        assertAggregateSyncStatusVectorEquals("foo/bar", true, true, true); // ss:anchor / agss:root
        assertAggregateSyncStatusVectorEquals("foo", true, true, true);
        assertAggregateSyncStatusVectorEquals("", true, true, true);
    }

    @Test
    public void shouldStopUpdateWhenCascadingStops() throws Exception
    {
        // initial state
        mds.root().agss(1, 0, 0)
                .dir("foo").ss(true, true, true).agss(1, 0, 0)
                        .dir("bar").ss(true, true, true).agss(1, 0, 0)
                                .dir("baz").ss(true, true, true).agss(2, 1, 0)
                                        .file("hello").ss(true, false, false).parent()
                                        .file("world").ss(true, true, false);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar/baz", true, false, false);
        assertAggregateSyncStatusVectorEquals("foo/bar", true, false, false);
        assertAggregateSyncStatusVectorEquals("foo", true, false, false);
        assertAggregateSyncStatusVectorEquals("", true, false, false);

        // state change
        mds.sync("foo/bar/baz/world", new BitVector(true, false, true), t, agsync);
        t.commit_();

        // verify expected interactions caused by state change
        assertAggregateSyncStatusEquals("foo/bar/baz", 2, 0, 1);
        assertAggregateSyncStatusEquals("foo/bar", 1, 0, 0);
        assertAggregateSyncStatusEquals("foo", 1, 0, 0);
        assertAggregateSyncStatusEquals("", 1, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar/baz", true, false, false);
        assertAggregateSyncStatusVectorEquals("foo/bar", true, false, false);
        assertAggregateSyncStatusVectorEquals("foo", true, false, false);
        assertAggregateSyncStatusVectorEquals("", true, false, false);
    }

    /**
     * Moves are resolved via a 2-step update: delete+create
     *
     * This is all fine and dandy until the first step propagates upward and touches objects that
     * are in an inconsistent state that can only be corrected by the second step. To make sure this
     * doesn't happen we have to order the steps by the depth of the path they touch. Which means
     * we need to test to exercise this behavior: upward and downward move
     */
    @Test
    public void shouldUpdateOnUpwardMove() throws Exception
    {
        // initial state
        mds.root().agss(0, 0, 0)
                .dir("foo").ss(false, false, true).agss(1, 1, 2)
                        .dir("bar").ss(false, false, true).agss(0, 0, 0)
                                .file("baz").ss(false, false, false).parent().parent()
                        .file("hello").ss(false, false, true).parent()
                        .file("world").ss(true, true, true);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar", false, false, false);
        assertAggregateSyncStatusVectorEquals("foo", false, false, false);
        assertAggregateSyncStatusVectorEquals("", false, false, false);

        // state change
        mds.move("foo/bar/baz", "foo/baz", t, agsync);
        t.commit_();

        assertAggregateSyncStatusEquals("foo/bar", 0, 0, 0);
        assertAggregateSyncStatusEquals("foo", 1, 1, 3);
        assertAggregateSyncStatusEquals("", 0, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar", true, true, true);
        assertAggregateSyncStatusVectorEquals("foo", false, false, false);
        assertAggregateSyncStatusVectorEquals("", false, false, false);
    }

    @Test
    public void shouldUpdateOnDownwardMove() throws Exception
    {
        // initial state
        mds.root().agss(0, 0, 0)
                .dir("foo").ss(false, false, true).agss(1, 1, 3)
                        .dir("bar").ss(false, false, true).agss(0, 0, 0).parent()
                        .file("baz").ss(false, false, false).parent()
                        .file("hello").ss(false, false, true).parent()
                        .file("world").ss(true, true, true);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar", true, true, true);
        assertAggregateSyncStatusVectorEquals("foo", false, false, false);
        assertAggregateSyncStatusVectorEquals("", false, false, false);

        // state change
        mds.move("foo/baz", "foo/bar/baz", t, agsync);
        t.commit_();

        assertAggregateSyncStatusEquals("foo/bar", 0, 0, 0);
        assertAggregateSyncStatusEquals("foo", 1, 1, 2);
        assertAggregateSyncStatusEquals("", 0, 0, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar", false, false, false);
        assertAggregateSyncStatusVectorEquals("foo", false, false, false);
        assertAggregateSyncStatusVectorEquals("", false, false, false);
    }
}
