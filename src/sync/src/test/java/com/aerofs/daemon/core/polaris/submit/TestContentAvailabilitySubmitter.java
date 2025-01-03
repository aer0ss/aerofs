package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.daemon.core.AsyncHttpClient.Function;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.polaris.WaldoAsyncClient;
import com.aerofs.daemon.core.polaris.api.LocationBatch;
import com.aerofs.daemon.core.polaris.api.LocationBatchResult;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.async.AsyncWorkGroupScheduler;
import com.aerofs.daemon.core.polaris.db.AvailableContentDatabase;
import com.aerofs.daemon.core.polaris.db.AvailableContentDatabase.AvailableContent;
import com.aerofs.daemon.core.polaris.db.PolarisSchema;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.id.SIndex;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.testlib.InMemorySQLiteDBCW;
import com.google.gson.Gson;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.nio.charset.Charset;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestContentAvailabilitySubmitter extends AbstractTest
{
    ContentAvailabilitySubmitter submitter;
    AvailableContentDatabase acdb;
    TransManager tm;
    @Mock WaldoAsyncClient client;
    @Mock CfgLocalDID did;
    @Mock CoreScheduler sched;
    @Mock CfgLocalUser cfgLocalUser;
    @Mock IMapSIndex2SID sidx2sid;
    @Mock IMapSID2SIndex sid2sidx;
    Queue<AbstractEBSelfHandling> scheduled = new ArrayDeque<>();

    List<Integer> calls = new ArrayList<>();
    SIndex sidx = new SIndex(1);
    SID sid = SID.rootSID(UserID.fromInternal("foo@bar.baz"));

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
        dbcw.init_();
        try (Statement s = dbcw.getConnection().createStatement()) {
            new PolarisSchema().create_(s, dbcw);
        }

        tm = new TransManager(new Trans.Factory(dbcw));
        acdb = new AvailableContentDatabase(dbcw);

        when(sid2sidx.get_(sid)).thenReturn(sidx);
        when(sid2sidx.getNullable_(sid)).thenReturn(sidx);
        when(sid2sidx.getLocalOrAbsentNullable_(sid)).thenReturn(sidx);
        when(sidx2sid.get_(sidx)).thenReturn(sid);
        when(sidx2sid.getNullable_(sidx)).thenReturn(sid);
        when(sidx2sid.getLocalOrAbsent_(sidx)).thenReturn(sid);

        doReturn(DID.generate()).when(did).get();
        doReturn(UserID.UNKNOWN_TEAM_SERVER).when(cfgLocalUser).get();

        // submits tasks to the single-threaded executorService one at a time,
        // and waits for completion
        doAnswer(invocation -> {
            l.trace("scheduling");
            scheduled.add((AbstractEBSelfHandling) invocation.getArguments()[0]);
            return null;
        }).when(sched).schedule(any(IEvent.class));

        doAnswer(invocation -> {
            sched.schedule(new AbstractEBSelfHandling() {
                @Override
                public void handle_() {
                    Object[] arg = invocation.getArguments();
                    try {
                        l.trace("handling request");
                        Function<HttpResponse, Boolean, Exception> function = (Function<HttpResponse, Boolean, Exception>) arg[3];

                        LocationBatch batch = (LocationBatch) arg[1];

                        Set<String> oids = new HashSet<>();
                        batch.available.forEach(op -> oids.add(op.oid));

                        calls.add(batch.available.size());

                        ((AsyncTaskCallback) arg[2]).onSuccess_(function
                                .apply(httpResponse(locationBatchResult(batch.available.size()))));
                        l.trace("request succeeded");
                    } catch (Throwable t) {
                        l.trace("request failed?");
                        ((AsyncTaskCallback) arg[2]).onFailure_(t);
                    }
                }
            });
            return null;
        }).when(client).post(any(), any(LocationBatch.class), any(AsyncTaskCallback.class), any());

        WaldoAsyncClient.Factory fact = mock(WaldoAsyncClient.Factory.class);
        when(fact.create()).thenReturn(client);

        calls.clear();
        submitter = new ContentAvailabilitySubmitter(fact, acdb, tm, new AsyncWorkGroupScheduler(sched),
                sidx2sid, sid2sidx);
        submitter.start_();
    }

    @Test
    public void shouldSubmitAvailableContentAndRemoveSuccessfulSubmissions() throws Exception {
        try (Trans t = tm.begin_()) {
            for (int i = 0; i < 10; i++)
                submitter.onSetVersion_(sidx, OID.generate(), 1, t);

            int count = countAcdb();
            assertEquals(10, count);
            t.commit_();
        }

        runScheduled_();

        int count = countAcdb();

        assertEquals(2, calls.size());
        assertEquals(10, (int) calls.get(0));
        assertEquals(1, (int) calls.get(1));
        assertEquals(0, count);
    }

    @Test
    public void shouldLimitSubmittedBatchSizeAndImmediatelyReschedule() throws Exception {
        try (Trans t = tm.begin_()) {
            for (int i = 0; i < 100; i++)
                submitter.onSetVersion_(sidx, OID.generate(), 1, t);

            int count = countAcdb();

            assertEquals(100, count);
            t.commit_();
        }

        // process
        runScheduled_();

        int count = countAcdb();

        assertEquals(4, calls.size());
        assertEquals(42, (int) calls.get(0));
        assertEquals(42, (int) calls.get(1));
        assertEquals(18, (int) calls.get(2));
        assertEquals(1, (int) calls.get(3));
        assertEquals(0, count);
    }

    @Test
    public void shouldRescheduleWhenTransactionsCommitWhileRequestInFlight() throws Exception {
        sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_() {
                for (int i = 0; i < 100; i++) {
                    try (Trans t = tm.begin_()) {
                        for (int j = 0; j < 2; j++) {
                            submitter.onSetVersion_(sidx, OID.generate(), 1, t);
                        }
                        t.commit_();
                    } catch (SQLException e) {
                        throw new RuntimeException();
                    }
                }
            }
        });

        runScheduled_();

        int total = 0;
        for (Integer call : calls) total += call;

        int count = countAcdb();

        l.trace("availability count: {}", count);
        l.trace("calls: {}", calls.size());

        assertEquals(0, count);
        assertEquals(200 + calls.size() - 1, total);
    }

    // add one error per batch, unless it's the last one we're checking
    private LocationBatchResult locationBatchResult(int size) {
        assert size > 0;
        List<Boolean> results = new ArrayList<>(size);
        if (size > 1) {
            results.add(false);
            for (int i = 1; i < size; i++) {
                results.add(true);
            }
        } else {
            results.add(true);
        }
        return new LocationBatchResult(results);
    }

    private HttpResponse httpResponse(LocationBatchResult result) {
        HttpResponse polarisResponse = mock(HttpResponse.class);
        doReturn(HttpResponseStatus.OK).when(polarisResponse).getStatus();
        doReturn(ChannelBuffers.copiedBuffer(new Gson().toJson(result), Charset.defaultCharset()))
                .when(polarisResponse).getContent();
        return polarisResponse;
    }

    private void runScheduled_() {
        AbstractEBSelfHandling ev;
        while ((ev = scheduled.poll()) != null) ev.handle_();
    }

    private int countAcdb() throws SQLException {
        int count = 0;
        try (IDBIterator<AvailableContent> list = acdb.listContent_()) {
            while (list.next_()) {
                list.get_();
                count++;
            }
        }
        return count;
    }
}
