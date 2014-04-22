/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.collector;

import com.aerofs.base.id.OID;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase.OCIDAndCS;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.OCID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.testlib.AbstractTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(value = Parameterized.class)
public class TestCollectorIterator extends AbstractTest
{
    private class MockDBIterator implements IDBIterator<OCIDAndCS> {
        private CollectorSeq _cs;
        private OCID _ocid;
        private int _n;
        private boolean _closed;

        MockDBIterator(CollectorSeq csStart, int limit)
        {
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
            _ocid = new OCID(OID.generate(), CID.META);
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

    @Mock Trans t;
    @Mock ICollectorSequenceDatabase csdb;
    @Mock CollectorSkipRule csr;

    SIndex sidx = new SIndex(1);
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
         when(csdb.getCS_(any(SIndex.class), any(CollectorSeq.class), anyInt())).then(
                 new Answer<IDBIterator<OCIDAndCS>>()
                 {
                     @Override
                     public IDBIterator<OCIDAndCS> answer(InvocationOnMock invocation)
                             throws Throwable
                     {
                         CollectorSeq csStart = (CollectorSeq)invocation.getArguments()[1];
                         int limit = (Integer)invocation.getArguments()[2];

                         return new MockDBIterator(csStart, limit);
                     }
                 }
         );

        it = new CollectorIterator(csdb, csr, sidx);
        Assert.assertFalse(it.started_());
    }

    private void iterate() throws SQLException
    {
        for (long i = firstCS; i <= lastCS; ++i) {
            Assert.assertTrue(it.next_(t));
            OCIDAndCS occs = it.current_();
            Assert.assertEquals(i, occs._cs.getLong());
        }

        Assert.assertFalse(it.next_(t));
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

        Assert.assertFalse(it.next_(t));

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
}
