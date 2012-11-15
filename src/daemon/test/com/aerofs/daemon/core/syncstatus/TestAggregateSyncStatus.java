/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.syncstatus;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.CounterVector;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.testlib.AbstractTest;
import junit.framework.Assert;
import org.hamcrest.Description;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.invocation.InvocationMatcher;
import org.mockito.internal.verification.api.VerificationData;
import org.mockito.invocation.Invocation;
import org.mockito.verification.VerificationMode;

import java.sql.SQLException;
import java.util.List;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

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

    @InjectMocks MockDS mds;
    @InjectMocks AggregateSyncStatus agsync;

    /**
     * A couple of remote device IDs to test sync status
     */
    final DID d0 = new DID(UniqueID.generate());
    final DID d1 = new DID(UniqueID.generate());
    final DID d2 = new DID(UniqueID.generate());

    /**
     * Custom matcher to check the path resolution of a SOID
     */
    static class IsSOIDAtPath extends ArgumentMatcher<SOID>
    {
        private final DirectoryService _ds;
        private final String _path;

        public IsSOIDAtPath(DirectoryService ds, String path)
        {
            _ds = ds;
            _path = path;
        }

        @Override
        public boolean matches(Object argument)
        {
            Assert.assertNotNull(argument);
            try {
                Path p = _ds.resolve_((SOID) argument);
                Assert.assertNotNull("expected " + argument + " to point to " + _path, p);
                return _path.equalsIgnoreCase(p.toStringFormal());
            } catch (SQLException e) {
                Assert.fail();
                return false;
            }
        }

        @Override
        public void describeTo(Description description)
        {
            description.appendText("<SOID @ ").appendValue(_path).appendText(">");
        }
    }

    /**
     * Helper to create SOID matcher
     */
    SOID soidAt(String path)
    {
        return argThat(new IsSOIDAtPath(ds, path));
    }

    /**
     * This class works around the limitations of Mockito in-order verification :
     *   - verification is greedy which makes it a pain to verify subsequent calls to the same
     *   method with the same parameters
     *   - verification is not strict which makes it hard to verify that no unexpected calls
     *   happened
     *
     * It is used to verify calls to ds.setAggregateSyncStatus which should follow a well-defined
     * pattern.
     */
    static class StrictlyOrderedNonGreedyVerification implements VerificationMode
    {
        private int _count;
        private int _index;

        StrictlyOrderedNonGreedyVerification()
        {
        }

        public int invocationCount()
        {
            return _count;
        }

        @Override
        public void verify(VerificationData data)
        {
            InvocationMatcher matcher = data.getWanted();
            Object mock = matcher.getInvocation().getMock();

            // find the next matching (mock, method) invocation
            List<Invocation> invocations = data.getAllInvocations();
            while (_index < invocations.size()) {
                Invocation invocation = invocations.get(_index);
                if (mock.equals(invocation.getMock()) && matcher.hasSameMethod(invocation)) {
                    break;
                }
                ++_index;
            }

            // check that we haven't reached the ned of the invocation list
            Assert.assertTrue(_index < invocations.size());

            // check that the current invocation matches the expectations
            Assert.assertTrue(matcher.matches(invocations.get(_index)));

            ++_count;
            ++_index;
        }
    }

    /**
     * Helper to verify updates to the aggregate sync status upon state change
     */
    int expectedAggregateSyncStat;
    StrictlyOrderedNonGreedyVerification dsVerificationMode;
    void verifySetAggregateSyncStatus(String path, int... counters) throws SQLException
    {
        ++expectedAggregateSyncStat;
        verify(ds, dsVerificationMode).setAggregateSyncStatus_(
                soidAt(path), eq(new CounterVector(counters)), eq(t));
    }

    void assertAggregateSyncStatusVectorEquals(String path, boolean... status) throws Exception
    {
        SOID soid = ds.resolveThrows_(Path.fromString(path));
        Assert.assertEquals(new BitVector(status), agsync.getAggregateSyncStatusVector_(soid));
    }

    @Before
    public void setup() throws Exception
    {
        expectedAggregateSyncStat = 0;
        dsVerificationMode = new StrictlyOrderedNonGreedyVerification();

        // stub device list of root store
        mds.dids(d0, d1, d2);
    }

    @After
    public void cleanup() throws Exception
    {
        // All test should specify exhaustively the expected aggregateSyncStat updates
        Assert.assertEquals(expectedAggregateSyncStat, dsVerificationMode.invocationCount());
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

        // verify expected interactions caused by state change
        verifySetAggregateSyncStatus("foo", 1, 0, 1);
        verifySetAggregateSyncStatus("", 0, 0, 0);

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

        // verify expected interactions caused by state change
        verifySetAggregateSyncStatus("foo", 1, 0, 1);
        verifySetAggregateSyncStatus("", 0, 0, 0);

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

        // verify expected interactions caused by state change
        verifySetAggregateSyncStatus("foo/bar", 1, 1, 0);
        verifySetAggregateSyncStatus("foo", 2, 2, 1);
        verifySetAggregateSyncStatus("", 1, 1, 0);

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

        // verify expected interactions caused by state change
        verifySetAggregateSyncStatus("foo", 1, 1, 0);
        verifySetAggregateSyncStatus("", 1, 1, 0);

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

        // verify expected interactions caused by state change
        verifySetAggregateSyncStatus("foo/bar", 1, 1, 0);
        verifySetAggregateSyncStatus("foo", 2, 2, 1);
        verifySetAggregateSyncStatus("", 1, 1, 0);
        verifySetAggregateSyncStatus("foo/baz", 1, 0, 1);
        verifySetAggregateSyncStatus("foo", 2, 1, 1);
        verifySetAggregateSyncStatus("", 1, 0, 0);

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

        // verify expected interactions caused by state change
        verifySetAggregateSyncStatus("foo", 1, 1, 0);
        verifySetAggregateSyncStatus("", 1, 1, 0);
        verifySetAggregateSyncStatus("foo/bar", 3, 2, 3);
        verifySetAggregateSyncStatus("foo", 1, 0, 0);
        verifySetAggregateSyncStatus("", 1, 0, 0);

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

        // verify expected interactions caused by state change
        verifySetAggregateSyncStatus("foo/bar", 1, 1, 2);
        verifySetAggregateSyncStatus("foo", 1, 1, 2);
        verifySetAggregateSyncStatus("", 0, 0, 1);

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

        // verify expected interactions caused by state change
        verifySetAggregateSyncStatus("foo", 2, 1, 1);
        verifySetAggregateSyncStatus("", 1, 0, 0);

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

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar/baz", true, false, false);
        assertAggregateSyncStatusVectorEquals("foo/bar", true, false, false);
        assertAggregateSyncStatusVectorEquals("foo", true, true, true);
        assertAggregateSyncStatusVectorEquals("", true, true, true);

        // state change
        mds.sync("foo/bar/baz/hello", new BitVector(false, true, true), t, agsync);

        // verify expected interactions caused by state change
        verifySetAggregateSyncStatus("foo/bar/baz", 1, 2, 1);
        verifySetAggregateSyncStatus("foo/bar", 0, 1, 0);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar/baz", false, true, false);
        assertAggregateSyncStatusVectorEquals("foo/bar", false, true, false);
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

        // verify expected interactions caused by state change
        verifySetAggregateSyncStatus("foo/bar/baz", 2, 0, 1);

        // check that aggregate status vector is derived properly from aggregate status counters
        assertAggregateSyncStatusVectorEquals("foo/bar/baz", true, false, false);
        assertAggregateSyncStatusVectorEquals("foo/bar", true, false, false);
        assertAggregateSyncStatusVectorEquals("foo", true, false, false);
        assertAggregateSyncStatusVectorEquals("", true, false, false);
    }
}
