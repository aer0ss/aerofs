/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.collector;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.daemon.core.CoreExponentialRetry;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.ContentFetchQueueDatabase;
import com.aerofs.daemon.core.polaris.db.ContentFetchQueueWrapper;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.polaris.fetch.ContentFetcherIterator;
import com.aerofs.daemon.core.polaris.fetch.ContentFetcherIterator.Filter.Action;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.core.store.StoreCreationOperators;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.core.tc.ITokenReclamationListener;
import com.aerofs.daemon.core.transfers.download.Downloads;
import com.aerofs.daemon.core.transfers.download.IDownloadCompletionListener;
import com.aerofs.daemon.lib.db.*;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.db.InMemoryCoreDBCW;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
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
import static org.mockito.Mockito.*;

public class TestCollector extends AbstractTest
{
    InMemoryCoreDBCW idbcw = new InMemoryCoreDBCW();
    StoreCreationOperators sco = mock(StoreCreationOperators.class);
    StoreDeletionOperators sdo = mock(StoreDeletionOperators.class);

    @Spy ICollectorFilterDatabase cfdb = new CollectorFilterDatabase(idbcw);
    @Spy ContentFetchQueueDatabase cfqdb = new ContentFetchQueueDatabase(idbcw, sco, sdo);
    @Spy ContentFetchQueueWrapper cfqw = new ContentFetchQueueWrapper(cfqdb);
    @Spy ICollectorStateDatabase cidb = new StoreDatabase(idbcw);
    @Spy CentralVersionDatabase cvdb = new CentralVersionDatabase(idbcw, sdo);
    @Spy RemoteContentDatabase rcdb = new RemoteContentDatabase(idbcw, sco, sdo);

    @Mock Trans t;
    @Mock TransManager tm;
    @Mock CoreScheduler sched;
    @Mock Downloads dls;
    @Mock ContentFetcherIterator.Filter filter;

    @InjectMocks CoreExponentialRetry er;
    @InjectMocks ContentFetcherIterator.Factory factIter;

    ContentFetcher collector;

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

        new StoreDatabase(idbcw).insert_(sidx, "", t);
        cfqdb.createStore_(sidx, t);
        rcdb.createStore_(sidx, t);

        ContentFetcher.Factory fact = new ContentFetcher.Factory(sched, dls, tm, er, cfdb,
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
    private MockAsyncDownload addVersionFor(final SOID soid) throws SQLException
    {
        MockAsyncDownload madl = new MockAsyncDownload() {
            @Override
            public void completed() {
                try {
                    when(filter.filter_(soid)).thenReturn(Action.Fetch);
                } catch (SQLException e) {
                    throw new AssertionError();
                }
            }
        };
        cfqw.insert_(soid.sidx(), soid.oid(), t);
        rcdb.insert_(soid.sidx(), soid.oid(), 42, DID.generate(), new ContentHash(BaseSecUtil.hash()), 0L, t);

        when(filter.filter_(soid)).thenReturn(Action.Fetch);
        when(dls.downloadAsync_(eq(soid), anySetOf(DID.class),
                any(ITokenReclamationListener.class),
                any(IDownloadCompletionListener.class))).thenAnswer(madl);
        return madl;
    }

    private void verifyDownloadRequest(SOID soid, DID... dids)
    {
        verifyDownloadRequest(soid, times(1), dids);
    }

    private void verifyDownloadRequest(SOID soid, VerificationMode mode, DID... dids)
    {
        verify(dls, mode).downloadAsync_(eq(soid), eq(ImmutableSet.copyOf(dids)),
                any(ITokenReclamationListener.class), any(IDownloadCompletionListener.class));
    }

    @Test
    public void shouldCollectOneComponent() throws Exception
    {
        SOID soid = new SOID(sidx, OID.generate());

        addVersionFor(soid);
        collector.add_(d0, BFOID.of(soid.oid()), t);

        verifyDownloadRequest(soid, d0);
    }

    @Test
    public void shouldCollectDifferentObjectsFromDifferentDevices() throws Exception
    {
        SOID o0 = new SOID(sidx, OID.generate());
        SOID o1 = new SOID(sidx, OID.generate());

        MockAsyncDownload madl = addVersionFor(o0);
        addVersionFor(o1);

        collector.online_(d1);
        collector.add_(d0, BFOID.of(o0.oid()), t);

        // if we don't simulate download completion the collector will issue a new downloadAsync
        // when it is restarted as a result of adding d1's bloom filter...
        madl.ok(d0);

        collector.add_(d1, BFOID.of(o1.oid()), t);

        verifyDownloadRequest(o0, d0);
        verifyDownloadRequest(o1, d1);
    }

    @Test
    public void shouldStopIteratingWhenRunningOutOfTokens() throws Exception
    {
        SOID o0 = new SOID(sidx, OID.generate());
        SOID o1 = new SOID(sidx, OID.generate());

        MockAsyncDownload madl = addVersionFor(o0);
        addVersionFor(o1);

        // simulate lack of Token for first download request
        madl.outOfToken();

        collector.add_(d0, BFOID.of(o0.oid()), t);

        verifyDownloadRequest(o0, d0);
        verifyNoMoreInteractions(dls);
    }

    @Test
    public void shouldResumeIteratingWhenTokenReclaimed() throws Exception
    {
        SOID o0 = new SOID(sidx, OID.generate());
        SOID o1 = new SOID(sidx, OID.generate());

        MockAsyncDownload madl = addVersionFor(o0);
        addVersionFor(o1);

        madl.outOfToken();

        collector.add_(d0, BFOID.of(o0.oid()), t);
        collector.add_(d0, BFOID.of(o1.oid()), t);

        // simulate availability of new token-> restart collection
        madl.reclaim();

        // two download requests are made for META: the first is rejected due to lack of Tokens
        verifyDownloadRequest(o0, times(2), d0);
        verifyDownloadRequest(o1, d0);
    }

    @Test
    public void shouldDiscard() throws Exception
    {
        SOID soid = new SOID(sidx, OID.generate());

        cfqw.insert_(sidx, soid.oid(), t);
        when(filter.filter_(soid)).thenReturn(Action.Ignore);

        collector.add_(d0, BFOID.of(soid.oid()), t);

        verifyZeroInteractions(dls);
    }

    @Test
    public void shouldNotDiscardWrongObject() throws Exception
    {
        SOID skip = new SOID(sidx, OID.generate());
        SOID soid = new SOID(sidx, OID.generate());

        cfqw.insert_(sidx, skip.oid(), t);
        when(filter.filter_(skip)).thenReturn(Action.Ignore);

        MockAsyncDownload madl = addVersionFor(soid);
        collector.add_(d0, BFOID.of(soid.oid()), t);

        // indicate that the first download is done
        madl.ok(d0);

        addVersionFor(soid);
        collector.add_(d0, BFOID.of(soid.oid()), t);

        verifyDownloadRequest(soid, times(2), d0);
    }
}
