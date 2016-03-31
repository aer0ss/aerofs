package com.aerofs.daemon.core.status;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.AsyncHttpClient.Function;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.polaris.api.LocationStatusBatch;
import com.aerofs.daemon.core.polaris.api.LocationStatusBatchResult;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;
import com.google.gson.Gson;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.slf4j.Logger;

import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.aerofs.daemon.lib.db.CoreSchema.C_OUT_OF_SYNC_FILES_TIMESTAMP;
import static com.aerofs.daemon.lib.db.CoreSchema.T_OUT_OF_SYNC_FILES;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * This class uses a real SyncStatusPropagator and in-memory DirectoryService as
 * well, in order to test the effects of the poller on the underlying data
 */
public class TestSyncStatusVerifier extends AbstractSyncStatusTest
{
    private static final Logger l = Loggers.getLogger(TestSyncStatusVerifier.class);
    @Mock CentralVersionDatabase centralVersionDatabase;
    @Mock PauseSync pauseSync;

    SyncStatusVerifier verifier;

    @Before
    public void before() throws Exception {
        l.trace("before");

        verifier = new SyncStatusVerifier(propagator, syncStatusOnline, coreScheduler, transManager,
                directoryService, outOfSyncDatabase, syncStatusRequests, centralVersionDatabase,
                statusChecker);

        doReturn(1L).when(centralVersionDatabase).getVersion_(any(), any());

        doReturn(false).when(pauseSync).isPaused();

        try (Trans trans = transManager.begin_()) {
            propagator.updateSyncStatus_(qux, false, trans);
            propagator.updateSyncStatus_(baz, false, trans);
            ageOutOfSyncFilesTimestamps();
            trans.commit_();
        }

        l.trace("end before");
    }

    @Test
    public void shouldMarkFilesSyncedWhenBackedUp() throws SQLException {
        mockPolarisResponse(syncedLocationBatchResult());

        statusChecker.completed = false;
        verifier.scheduleVerifyUnsyncedFilesImmediate(0L);

        pause(2);

        assertTrue(directoryService.getOA_(baz).synced());
        assertTrue(directoryService.getOA_(qux).synced());
        assertTrue(directoryService.getOA_(empty).synced());
        assertTrue(directoryService.getOA_(anchor).synced());
        assertTrue(directoryService.getOA_(deep).synced());
        assertTrue(directoryService.getOA_(inside).synced());
        assertTrue(directoryService.getOA_(the).synced());
        assertTrue(directoryService.getOA_(moria).synced());
        assertTrue(directoryService.getOA_(bar).synced());
        assertTrue(directoryService.getOA_(foo).synced());
        assertTrue(directoryService.getOA_(new SOID(rootSIndex, OID.ROOT)).synced());
    }

    @Test
    public void shouldNotMarkFilesSyncedWhenNotBackedUp() throws Exception {
        mockPolarisResponse(notSyncedLocationBatchResult());

        statusChecker.completed = false;
        verifier.scheduleVerifyUnsyncedFilesImmediate(0L);

        pause(2);

        assertTrue(directoryService.getOA_(empty).synced());
        assertTrue(directoryService.getOA_(anchor).synced());
        assertTrue(directoryService.getOA_(deep).synced());
        assertTrue(directoryService.getOA_(inside).synced());
        assertTrue(directoryService.getOA_(the).synced());
        assertTrue(directoryService.getOA_(moria).synced());
        assertFalse(directoryService.getOA_(bar).synced());
        assertFalse(directoryService.getOA_(foo).synced());
        assertFalse(directoryService.getOA_(new SOID(rootSIndex, OID.ROOT)).synced());
        assertFalse(directoryService.getOA_(baz).synced());
        assertFalse(directoryService.getOA_(qux).synced());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSubmitSeparateRequestsForSeparatePagesWhenEnoughOOSFiles() throws Exception {
        try (Trans trans = transManager.begin_()) {
            for (int i = 0; i < 100; i++) {
                OID oid = OID.generate();
                directoryService.createOA_(Type.FILE, rootSIndex, oid, the.oid(), String.valueOf(i),
                        trans);
                SOID soid = new SOID(rootSIndex, oid);
                propagator.updateSyncStatus_(soid, false, trans);
                propagator.updateSyncStatus_(soid, false, trans);
            }
            ageOutOfSyncFilesTimestamps();
            trans.commit_();
        }

        List<Set<String>> statusQueries = new ArrayList<>();
        doAnswer(invocation -> {
            polarisExecutor.submit(() -> {

                Object[] arg = invocation.getArguments();
                try {
                    Function<HttpResponse, Boolean, Exception> function = (Function<HttpResponse, Boolean, Exception>) arg[2];

                    HttpRequest request = (HttpRequest) arg[0];
                    ChannelBuffer content = request.getContent();
                    String json = content.toString(Charset.defaultCharset());
                    LocationStatusBatch batch = new Gson().fromJson(json, LocationStatusBatch.class);

                    Set<String> oids = new HashSet<String>();
                    batch.objects.forEach(op -> oids.add(op.oid));
                    statusQueries.add(oids);
                    l.trace("querying polaris for {} locations", batch.objects.size());

                    schedExecutor.submit(() -> {
                        ((AsyncTaskCallback) arg[1]).onSuccess_(function.apply(polarisResponse(
                                halfSyncedLocationBatchResult(batch.objects.size()))));
                        return null;
                    });
                } catch (Throwable t) {
                    ((AsyncTaskCallback) arg[1]).onFailure_(t);
                }
            });
            return null;
        }).when(waldoClient).send(any(HttpRequest.class), any(AsyncTaskCallback.class), any());

        int oosCount = countOutOfSyncFiles();
        assertEquals(102, oosCount);

        verifier.scheduleVerifyUnsyncedFilesImmediate(0L);

        pause(8);

        Set<String> combined = new HashSet<>();
        statusQueries.forEach(oids -> combined.addAll(oids));

        oosCount = countOutOfSyncFiles();

        assertEquals(3, statusQueries.size());
        assertEquals(102, combined.size());
        assertEquals(40, statusQueries.get(0).size());
        assertEquals(40, statusQueries.get(1).size());
        assertEquals(22, statusQueries.get(2).size());
        assertEquals(51, oosCount);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldRemoveExpelledObjectsFromOOSFilesDatabase() throws Exception {
        try (Trans trans = transManager.begin_()) {
            for (int i = 1; i <= 225; i++) {
                OID oid = OID.generate();
                metaDatabase.insertOA_(rootSIndex, oid, the.oid(), String.valueOf(i), Type.FILE,
                        OA.FLAG_EXPELLED_ORG, trans);
                outOfSyncDatabase.insert_(rootSIndex, oid, trans);
            }
            for (int i = 226; i <= 240; i++) {
                OID oid = OID.generate();
                directoryService.createOA_(Type.FILE, rootSIndex, oid, the.oid(), String.valueOf(i),
                        trans);
                SOID soid = new SOID(rootSIndex, oid);
                propagator.updateSyncStatus_(soid, false, trans);
            }
            ageOutOfSyncFilesTimestamps();
            trans.commit_();
        }

        List<Set<String>> statusQueries = new ArrayList<>();
        doAnswer(invocation -> {
            polarisExecutor.submit(() -> {
                Object[] arg = invocation.getArguments();
                try {
                    Function<HttpResponse, Boolean, Exception> function = (Function<HttpResponse, Boolean, Exception>) arg[2];

                    HttpRequest request = (HttpRequest) arg[0];
                    ChannelBuffer content = request.getContent();
                    String json = content.toString(Charset.defaultCharset());
                    LocationStatusBatch batch = new Gson().fromJson(json, LocationStatusBatch.class);

                    Set<String> oids = new HashSet<String>();
                    batch.objects.forEach(op -> oids.add(op.oid));
                    statusQueries.add(oids);

                    schedExecutor.submit(() -> {
                        ((AsyncTaskCallback) arg[1]).onSuccess_(function.apply(polarisResponse(
                                halfSyncedLocationBatchResult(batch.objects.size()))));
                        return null;
                    });
                } catch (Throwable t) {
                    ((AsyncTaskCallback) arg[1]).onFailure_(t);
                }
            });
            return null;
        }).when(waldoClient).send(any(HttpRequest.class), any(AsyncTaskCallback.class), any());

        verifier.scheduleVerifyUnsyncedFilesImmediate(0L);

        pause(10);

        Set<String> combined = new HashSet<>();
        statusQueries.forEach(oid -> combined.addAll(oid));

        int oosCount = countOutOfSyncFiles();

        assertEquals(17, combined.size());

        int expectedOosCount = 0;
        for (Set<String> oids : statusQueries) {
            l.trace("query size: {}", oids.size());
            expectedOosCount += oids.size() / 2;
        }

        assertEquals(expectedOosCount, oosCount);
    }

    @SuppressWarnings("unchecked")
    private void mockPolarisResponse(LocationStatusBatchResult result) {
        doAnswer(invocation -> {
            polarisExecutor.submit(() -> {
                Object[] arg = invocation.getArguments();
                try {
                    Function<HttpResponse, Boolean, Exception> function = (Function<HttpResponse, Boolean, Exception>) arg[2];

                    schedExecutor.submit(() -> {
                        ((AsyncTaskCallback) arg[1]).onSuccess_(function.apply(polarisResponse(result)));
                        return null;
                    });
                } catch (Throwable t) {
                    ((AsyncTaskCallback) arg[1]).onFailure_(t);
                }
            });
            return null;
        }).when(waldoClient).send(any(), any(AsyncTaskCallback.class), any());
    }

    private HttpResponse polarisResponse(LocationStatusBatchResult result) {
        HttpResponse polarisResponse = mock(HttpResponse.class);
        doReturn(HttpResponseStatus.OK).when(polarisResponse).getStatus();
        doReturn(ChannelBuffers.copiedBuffer(new Gson().toJson(result), Charset.defaultCharset()))
                .when(polarisResponse).getContent();
        return polarisResponse;
    }

    private LocationStatusBatchResult notSyncedLocationBatchResult() {
        return new LocationStatusBatchResult(Lists.newArrayList(false, false));
    }

    private LocationStatusBatchResult syncedLocationBatchResult() {
        return new LocationStatusBatchResult(Lists.newArrayList(true, true));
    }

    private LocationStatusBatchResult halfSyncedLocationBatchResult(int size) {
        List<Boolean> results = new ArrayList<>(size);
        for (int i = 0; i < size; i += 2)
            results.add(true);
        return new LocationStatusBatchResult(results);
    }

    private void pause(int seconds) {
        long currentTimeMillis = System.currentTimeMillis();
        while (System.currentTimeMillis() - currentTimeMillis < seconds * 1000);
    }

    // SyncStatusVerifier ignores files recently marked out-of-sync, so this is
    // necessary to test its functionality
    private boolean ageOutOfSyncFilesTimestamps() throws SQLException {
        return 1 == outOfSyncDatabase.update(
                new PreparedStatementWrapper(
                        DBUtil.update(T_OUT_OF_SYNC_FILES, C_OUT_OF_SYNC_FILES_TIMESTAMP).toString()),
                System.currentTimeMillis() - verifier.IGNORE_WINDOW);
    }
}
