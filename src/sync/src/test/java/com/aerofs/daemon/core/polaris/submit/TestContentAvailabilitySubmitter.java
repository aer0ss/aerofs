package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.daemon.core.AsyncHttpClient.Function;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.polaris.WaldoAsyncClient;
import com.aerofs.daemon.core.polaris.api.*;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.async.AsyncWorkGroupScheduler;
import com.aerofs.daemon.core.polaris.db.AvailableContentDatabase;
import com.aerofs.daemon.core.polaris.db.AvailableContentDatabase.AvailableContent;
import com.aerofs.daemon.core.polaris.db.PolarisSchema;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;

import java.nio.charset.Charset;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class TestContentAvailabilitySubmitter extends AbstractTest
{
    ContentAvailabilitySubmitter submitter;
    AvailableContentDatabase acdb;
    TransManager tm;
    StoreDeletionOperators sdo;
    @Mock WaldoAsyncClient client;
    @Mock CfgLocalDID did;
    @Mock CoreScheduler sched;
    @Mock CfgLocalUser cfgLocalUser;
    @Mock IMapSIndex2SID sidx2sid;
    @Mock IMapSID2SIndex sid2sidx;
    ExecutorService schedExecutor;

    List<Integer> calls = new ArrayList<>();
    SIndex sidx = new SIndex(1);
    SID sid = SID.rootSID(UserID.fromInternal("foo@bar.baz"));

    @After
    public void tearDown() {
        schedExecutor.shutdown();
    }

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        schedExecutor = Executors.newSingleThreadExecutor();
        InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
        dbcw.init_();
        try (Statement s = dbcw.getConnection().createStatement()) {
            new PolarisSchema().create_(s, dbcw);
            PolarisSchema.createAvailableContentTable(s, dbcw);
        }

        tm = new TransManager(new Trans.Factory(dbcw));
        sdo = new StoreDeletionOperators();
        acdb = new AvailableContentDatabase(dbcw, sdo);

        when(sid2sidx.getNullable_(sid)).thenReturn(sidx);
        when(sidx2sid.getNullable_(sidx)).thenReturn(sid);

        doReturn(DID.generate()).when(did).get();
        doReturn(UserID.UNKNOWN_TEAM_SERVER).when(cfgLocalUser).get();

        // submits tasks to the single-threaded executorService one at a time,
        // and waits for completion
        doAnswer(invocation -> {
            l.trace("scheduling");
            schedExecutor.submit(() -> {
                l.trace("running");
                ((AbstractEBSelfHandling) invocation.getArguments()[0]).handle_();
                l.trace("done running");
                return null;
            });
            return null;
        }).when(sched).schedule_(any(IEvent.class));

        doAnswer(invocation -> {
            Object[] arg = invocation.getArguments();
            try {
                Function<HttpResponse, Boolean, Exception> function = (Function<HttpResponse, Boolean, Exception>) arg[3];

                LocationBatch batch = (LocationBatch) arg[1];

                Set<String> oids = new HashSet<String>();
                batch.available.forEach(op -> oids.add(op.oid));

                calls.add(batch.available.size());

                schedExecutor.submit(() -> {
                    ((AsyncTaskCallback) arg[2]).onSuccess_(function
                            .apply(polarisResponse(locationBatchResult(batch.available.size()))));
                    return null;
                });
                l.trace("request succeeded");
            } catch (Throwable t) {
                l.trace("request failed?");
                ((AsyncTaskCallback) arg[2]).onFailure_(t);
            }
            return null;
        }).when(client).post(any(), any(LocationBatch.class), any(AsyncTaskCallback.class), any());

        WaldoAsyncClient.Factory fact = mock(WaldoAsyncClient.Factory.class);
        when(fact.create()).thenReturn(client);

        calls.clear();
        submitter = new ContentAvailabilitySubmitter(fact, acdb, tm,
                new AsyncWorkGroupScheduler(sched), sidx2sid, sid2sidx);
        submitter.start_();
        pause();
    }

    @Test
    @Ignore
    public void shouldSubmitAvailableContentAndRemoveSuccessfulSubmissions() throws Exception {

        try (Trans t = tm.begin_()) {
            for (int i = 0; i < 10; i++)
                submitter.onSetVersion_(sidx, OID.generate(), 1, t);

            int count = 0;
            IDBIterator<AvailableContent> list = acdb.listContent_();
            while (list.next_()) {
                list.get_();
                count++;
            }
            assertEquals(10, count);
            t.commit_();
        }

        pause(50, 2);

        int count = 0;
        IDBIterator<AvailableContent> list = acdb.listContent_();
        while (list.next_()) {
            list.get_();
            count++;
        }

        assertEquals(2, calls.size());
        assertEquals(10, (int) calls.get(0));
        assertEquals(1, (int) calls.get(1));
        assertEquals(0, count);
    }

    @Test
    @Ignore
    public void shouldLimitSubmittedBatchSizeAndImmediatelyReschedule() throws Exception {

        Trans t = tm.begin_();
        for (int i = 0; i < 100; i++)
            submitter.onSetVersion_(sidx, OID.generate(), 1, t);

        int count = 0;
        IDBIterator<AvailableContent> list = acdb.listContent_();
        while (list.next_()) {
            list.get_();
            count++;
        }

        assertEquals(100, count);
        t.commit_();
        t.end_();

        pause(50, 4);

        count = 0;
        list = acdb.listContent_();
        while (list.next_()) {
            list.get_();
            count++;
        }

        assertEquals(4, calls.size());
        assertEquals(42, (int) calls.get(0));
        assertEquals(42, (int) calls.get(1));
        assertEquals(18, (int) calls.get(2));
        assertEquals(1, (int) calls.get(3));
        assertEquals(0, count);
    }

    @Test
    @Ignore
    public void shouldRescheduleWhenTransactionsCommitWhileRequestInFlight() throws Exception {
        sched.schedule_(new AbstractEBSelfHandling() {
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

        int total = 0;
        for (int i = 0; i < 50 && total != 200 + calls.size() - 1; i++) {
            pause();
            total = 0;
            for (Integer call : calls)
                total += call;
        }

        int count = 0;
        IDBIterator<AvailableContent> list = acdb.listContent_();
        while (list.next_()) {
            list.get_();
            count++;
        }

        l.trace("availability count: {}", count);
        l.trace("calls: {}", calls.size());

        total = 0;
        for (Integer call : calls)
            total += call;

        assertEquals(0, count);
        assertEquals(200 + calls.size() - 1, total);
    }

    private LocationBatchResult locationBatchResult(int size) {
        assert size > 0;
        List<Boolean> results = new ArrayList<>(size);
        if (size > 1) {
            results.add(false);
            // why is the error coming first?
            for (int i = 1; i < size; i++) {
                results.add(true);
            }
        } else {
            results.add(true);
        }
        return new LocationBatchResult(results);
    }

    private HttpResponse polarisResponse(LocationBatchResult result) {
        HttpResponse polarisResponse = mock(HttpResponse.class);
        doReturn(HttpResponseStatus.OK).when(polarisResponse).getStatus();
        doReturn(ChannelBuffers.copiedBuffer(new Gson().toJson(result), Charset.defaultCharset()))
                .when(polarisResponse).getContent();
        return polarisResponse;
    }

    private void pause() {
        long currentTimeMillis = System.currentTimeMillis();
        while (System.currentTimeMillis() - currentTimeMillis < 2500);
    }

    private void pause(int times, int numCalls) {
        for (int i = 0; i < times && calls.size() < numCalls; i++)
            pause();
    }
}
