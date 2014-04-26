/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.collector;

import com.aerofs.base.id.OID;
import com.aerofs.daemon.lib.db.ICollectorStateDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase.OCIDAndCS;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.OCID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(value = Parameterized.class)
public class TestCollectorIterator extends AbstractTest
{
    private class MockDBIterator implements IDBIterator<OCIDAndCS> {
        private final CID _cid;
        private CollectorSeq _cs;
        private OCID _ocid;
        private int _n;
        private boolean _closed;

        /**
         * @param cid the component ID get_() will return
         */
        MockDBIterator(CollectorSeq csStart, int limit, CID cid)
        {
            _cid = cid;
            _cs = csStart;
            _n = limit;
        }

        @Override
        public OCIDAndCS get_() throws SQLException
        {
            return _closed || _n < 0 || _cs.getLong() > lastCS ?
                    null : new OCIDAndCS(_ocid, _cs);
        }

        @Override
        public boolean next_() throws SQLException
        {
            if (_closed || _n <= 0 ||
                    (_cs != null ? _cs.getLong() : firstCS - 1) >= lastCS) {
                return false;
            }

            _cs = _cs != null ? _cs.plusOne() : new CollectorSeq(firstCS);
            _ocid = new OCID(OID.generate(), _cid);
            --_n;
            return true;
        }

        @Override
        public void close_() throws SQLException
        {
            _closed = true;
        }

        @Override
        public boolean closed_()
        {
            return _closed;
        }
    }

    private class CSDBAnswer implements Answer<IDBIterator<OCIDAndCS>> {
        private final CID _cid;

        CSDBAnswer(CID cid)
        {
            _cid = cid;
        }

        @Override
        public IDBIterator<OCIDAndCS> answer(InvocationOnMock invocation)
                throws Throwable
        {
            CollectorSeq csStart = (CollectorSeq)invocation.getArguments()[1];
            int limit = (Integer)invocation.getArguments()[2];

            return new MockDBIterator(csStart, limit, _cid);
        }
    }

    InMemorySQLiteDBCW idbcw = new InMemorySQLiteDBCW();
    @Spy ICollectorStateDatabase cidb = new StoreDatabase(idbcw.getCoreDBCW());

    @Mock Trans t;
    @Mock ICollectorSequenceDatabase csdb;
    @Mock CollectorSkipRule csr;

    @InjectMocks CollectorIterator.Factory fact;

    final SIndex sidx = new SIndex(1);
    // The CID the mocked csdb.getCS() returns
    final CID cidOfGetCS = new CID(123);
    // The CID the mocked csdb.getMetaCS() returns
    final CID cidOfGetMetaCS = new CID(321);
    CollectorIterator it;

    private final long firstCS;
    private final long lastCS;

    public TestCollectorIterator(long firstCS, long lastCS)
    {
        this.firstCS = firstCS;
        this.lastCS = lastCS;
    }

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {10, 9},                                    // empty queue
                {10, 19},                                   // 10 objects
                {0, CollectorIterator.FETCH_SIZE},           // exercise batch fetch
                {0, CollectorIterator.SHRINK_THRESHOLD},     // exercise cache clear
        });
    }

    @Before
    public void setUp() throws Exception
    {
        when(csdb.getCS_(any(SIndex.class), any(CollectorSeq.class), anyInt()))
                .then(new CSDBAnswer(cidOfGetCS));
        when(csdb.getMetaCS_(any(SIndex.class), any(CollectorSeq.class), anyInt()))
                .then(new CSDBAnswer(cidOfGetMetaCS));

        idbcw.init_();
        new StoreDatabase(idbcw.getCoreDBCW()).insert_(sidx, "", t);

        // Collect content by default
        it = fact.create_(sidx);
        assertFalse(it.started_());
    }

    private void iterate() throws SQLException
    {
        for (long i = firstCS; i <= lastCS; ++i) {
            assertTrue(it.next_(t));
            OCIDAndCS occs = it.current_();
            assertEquals(i, occs._cs.getLong());
        }

        assertFalse(it.next_(t));
    }

    @Test
    public void shouldIterateAll() throws Exception
    {
        iterate();
        verify(csdb, never()).deleteCS_(any(CollectorSeq.class), any(Trans.class));
    }

    @Test
    public void shouldDiscardAll() throws Exception
    {
        when(csr.shouldSkip_(any(SOCID.class))).thenReturn(true);

        assertFalse(it.next_(t));

        for (long i = firstCS; i <= lastCS; ++i) {
            verify(csdb).deleteCS_(new CollectorSeq(i), t);
        }
    }

    @Test
    public void shouldIterateAfterReset() throws Exception
    {
        iterate();

        it.reset_();

        iterate();

        verify(csdb, never()).deleteCS_(any(CollectorSeq.class), any(Trans.class));
    }

    @Test
    public void shouldSwitchToContentExclusionAndClearCacheAsExpected()
            throws SQLException
    {
        // by default the content is included
        it.next_(t);
        verify(csdb).getCS_(any(SIndex.class), any(CollectorSeq.class), anyInt());
        assertCurrentCID(cidOfGetCS);

        // verify no calls to getMetaCS_()
        verifyNoMoreInteractions(csdb);

        assertTrue(it.excludeContent_(t));
        it.next_(t);
        // verify that a call to csdb is made.
        verify(csdb).getMetaCS_(any(SIndex.class), any(CollectorSeq.class), anyInt());
        // verify that all cached entries from the getCS call has been cleared.
        assertCurrentCID(cidOfGetMetaCS);

        // verify no calls to getCS_()
        verifyNoMoreInteractions(csdb);
    }

    @Test
    public void shouldSwitchToContentInclusionAndClearCacheAsExpected()
            throws SQLException
    {
        assertTrue(it.excludeContent_(t));
        it.next_(t);
        verify(csdb).getMetaCS_(any(SIndex.class), any(CollectorSeq.class), anyInt());
        assertCurrentCID(cidOfGetMetaCS);

        // verify no calls to getCS_()
        verifyNoMoreInteractions(csdb);

        assertTrue(it.includeContent_(t));
        it.next_(t);
        // verify that a call to csdb is made.
        verify(csdb).getCS_(any(SIndex.class), any(CollectorSeq.class), anyInt());
        // verify that all cached entries from the getmetaCS call has been cleared.
        assertCurrentCID(cidOfGetCS);

        // verify no calls to getMetaCS_()
        verifyNoMoreInteractions(csdb);
    }

    /**
     * Assert that either the current pointer of the iterator is null or the CID of the current
     * pointer is the specified value
     */
    private void assertCurrentCID(CID cid)
    {
        OCIDAndCS ret = it.currentNullable_();
        if (ret != null) assertEquals(cid, ret._ocid.cid());
    }

    @Test
    public void shouldNotClearCacheOnMultipleIncludeContentCalls()
            throws SQLException
    {
        // Skip the test if the cache has to be invalidated (due to reaching the end of the
        // collector queue) within two it.next() calls.
        assumeTrue(lastCS - firstCS > 0);

        // Content is included by default
        it.next_(t);
        verify(csdb).getCS_(any(SIndex.class), any(CollectorSeq.class), anyInt());
        verify(csdb, never()).getMetaCS_(any(SIndex.class), any(CollectorSeq.class), anyInt());

        assertFalse(it.includeContent_(t));
        it.next_(t);
        // The iterator should use the cached values rather than querying the csdb.
        verifyNoMoreInteractions(csdb);
    }


    @Test
    public void shouldNotClearCacheOnMultipleExcludeContentCalls()
            throws SQLException
    {
        // Skip the test if the cache has to be invalidated (due to reaching the end of the
        // collector queue) within two it.next() calls.
        assumeTrue(lastCS - firstCS > 0);

        assertTrue(it.excludeContent_(t));
        it.next_(t);
        verify(csdb).getMetaCS_(any(SIndex.class), any(CollectorSeq.class), anyInt());
        verify(csdb, never()).getCS_(any(SIndex.class), any(CollectorSeq.class), anyInt());

        assertFalse(it.excludeContent_(t));
        it.next_(t);
        // The iterator should use the cached values rather than querying the csdb.
        verifyNoMoreInteractions(csdb);
    }
}
