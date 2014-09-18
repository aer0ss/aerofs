/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.collector;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.CoreExponentialRetry;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.transfers.download.IDownloadCompletionListener;
import com.aerofs.daemon.core.transfers.download.Downloads;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.core.tc.ITokenReclamationListener;
import com.aerofs.daemon.lib.db.CollectorFilterDatabase;
import com.aerofs.daemon.lib.db.CollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.ICollectorFilterDatabase;
import com.aerofs.daemon.lib.db.ICollectorStateDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.ITransListener;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.verification.VerificationMode;

import java.sql.SQLException;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TestCollector extends AbstractTest
{
    InMemorySQLiteDBCW idbcw = new InMemorySQLiteDBCW();
    @Spy ICollectorFilterDatabase cfdb = new CollectorFilterDatabase(idbcw.getCoreDBCW());
    @Spy ICollectorSequenceDatabase csdb = new CollectorSequenceDatabase(idbcw.getCoreDBCW());
    @Spy ICollectorStateDatabase cidb = new StoreDatabase(idbcw.getCoreDBCW());

    @Mock Trans t;
    @Mock TransManager tm;
    @Mock CoreScheduler sched;
    @Mock CollectorSkipRule csr;
    @Mock Downloads dls;

    @InjectMocks CoreExponentialRetry er;
    @InjectMocks CollectorIterator.Factory factIter;

    Collector collector;

    SIndex sidx = new SIndex(1);
    DID d0 = DID.generate();
    DID d1 = DID.generate();

    @Before
    public void setUp() throws Exception
    {
        AppRoot.set("dummy");

        idbcw.init_();

        when(tm.begin_()).thenReturn(t);
        doAnswer(invocation -> {
            ((ITransListener)invocation.getArguments()[0]).committed_();
            return null;
        }).when(t).addListener_(any(ITransListener.class));

        doAnswer(invocation -> {
            l.info("sched");
            ((AbstractEBSelfHandling)invocation.getArguments()[0]).handle_();
            return null;
        }).when(sched).schedule(any(IEvent.class), anyLong());

        Store store = mock(Store.class);
        when(store.sidx()).thenReturn(sidx);

        new StoreDatabase(idbcw.getCoreDBCW()).insert_(sidx, "", false, t);


        Collector.Factory fact = new Collector.Factory(sched, csdb, csr, dls, tm, er, cfdb,
                factIter);
        collector = fact.create_(sidx);

        // test device online by default
        collector.online_(d0);
    }

    @After
    public void tearDown() throws Exception
    {
        idbcw.fini_();
    }

    /**
     * simulate the effect of receiving a new tick about an object:
     * 1. update CollectorSkipRule to not skip the object
     * 2. add CS if needed
     *
     * @return a mock download object to specify the behavior to simulate upon collection attempt
     *
     * NB: the download behavior will be repeated for however many attempts the collector makes,
     * until it is overriden by another call to addVersion (or alternatively by modifying the mock)
     */
    private MockAsyncDownload addVersionFor(final SOCID socid) throws SQLException
    {
        MockAsyncDownload madl = new MockAsyncDownload() {
            @Override
            public void completed() {
                try {
                    when(csr.shouldSkip_(socid)).thenReturn(true);
                } catch (SQLException e) {
                    throw new AssertionError();
                }
            }
        };
        csdb.insertCS_(socid, t);
        when(csr.shouldSkip_(socid)).thenReturn(false);
        when(dls.downloadAsync_(eq(socid), anySetOf(DID.class),
                any(ITokenReclamationListener.class),
                any(IDownloadCompletionListener.class))).thenAnswer(madl);
        return madl;
    }

    private void verifyDownloadRequest(SOCID socid, DID... dids)
    {
        verifyDownloadRequest(socid, times(1), dids);
    }

    private void verifyDownloadRequest(SOCID socid, VerificationMode mode, DID... dids)
    {
        verify(dls, mode).downloadAsync_(eq(socid), eq(ImmutableSet.copyOf(dids)),
                any(ITokenReclamationListener.class), any(IDownloadCompletionListener.class));
    }

    @Test
    public void shouldCollectOneComponent() throws Exception
    {
        SOCID socid = new SOCID(sidx, OID.generate(), CID.META);

        addVersionFor(socid);
        collector.add_(d0, BFOID.of(socid.oid()), t);

        verifyDownloadRequest(socid, d0);
    }

    @Test
    public void shouldCollectTwoComponentsSameObject() throws Exception
    {
        SOCID meta = new SOCID(sidx, OID.generate(), CID.META);
        SOCID content = new SOCID(meta.soid(), CID.CONTENT);

        addVersionFor(meta);
        addVersionFor(content);

        collector.add_(d0, BFOID.of(meta.oid()), t);

        verifyDownloadRequest(meta, d0);
        verifyDownloadRequest(content, d0);
    }

    @Test
    public void shouldCollectDifferentObjetsFromDifferentDevices() throws Exception
    {
        SOCID meta = new SOCID(sidx, OID.generate(), CID.META);
        SOCID content = new SOCID(sidx, OID.generate(), CID.CONTENT);

        MockAsyncDownload madl = addVersionFor(meta);
        addVersionFor(content);

        collector.online_(d1);
        collector.add_(d0, BFOID.of(meta.oid()), t);

        // if we don't simulate download completion the collector will issue a new downloadAsync
        // when it is restarted as a result of adding d1's bloom filter...
        madl.ok(d0);

        collector.add_(d1, BFOID.of(content.oid()), t);

        verifyDownloadRequest(meta, d0);
        verifyDownloadRequest(content, d1);
    }

    @Test
    public void shouldStopIteratingWhenRunningOutOfTokens() throws Exception
    {
        SOCID meta = new SOCID(sidx, OID.generate(), CID.META);
        SOCID content = new SOCID(meta.soid(), CID.CONTENT);

        MockAsyncDownload madl = addVersionFor(meta);
        addVersionFor(content);

        // simulate lack of Token for first download request
        madl.outOfToken();

        collector.add_(d0, BFOID.of(meta.oid()), t);

        verifyDownloadRequest(meta, d0);
        verifyNoMoreInteractions(dls);
    }

    @Test
    public void shouldResumeIteratingWhenTokenReclaimed() throws Exception
    {
        SOCID meta = new SOCID(sidx, OID.generate(), CID.META);
        SOCID content = new SOCID(meta.soid(), CID.CONTENT);

        MockAsyncDownload madl = addVersionFor(meta);
        addVersionFor(content);

        madl.outOfToken();

        collector.add_(d0, BFOID.of(meta.oid()), t);

        // simulate availability of new token-> restart collection
        madl.reclaim();

        // two download requests are made for META: the first is rejected due to lack of Tokens
        verifyDownloadRequest(meta, times(2), d0);
        verifyDownloadRequest(content, d0);
    }

    @Test
    public void shouldDiscard() throws Exception
    {
        SOCID socid = new SOCID(sidx, OID.generate(), CID.META);

        csdb.insertCS_(socid, t);
        when(csr.shouldSkip_(socid)).thenReturn(true);

        collector.add_(d0, BFOID.of(socid.oid()), t);

        verifyZeroInteractions(dls);
    }

    @Test
    public void shouldNotDiscardWrongObject() throws Exception
    {
        SOCID skip = new SOCID(sidx, OID.generate(), CID.CONTENT);
        SOCID socid = new SOCID(sidx, OID.generate(), CID.META);

        csdb.insertCS_(skip, t);
        when(csr.shouldSkip_(skip)).thenReturn(true);

        MockAsyncDownload madl = addVersionFor(socid);
        collector.add_(d0, BFOID.of(socid.oid()), t);

        // indicate that the first download is done
        madl.ok(d0);

        addVersionFor(socid);
        collector.add_(d0, BFOID.of(socid.oid()), t);

        verifyDownloadRequest(socid, times(2), d0);
    }
}
