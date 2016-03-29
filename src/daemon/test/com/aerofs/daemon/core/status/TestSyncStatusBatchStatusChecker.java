package com.aerofs.daemon.core.status;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.AsyncHttpClient.Function;
import com.aerofs.daemon.core.net.DeviceToUserMapper;
import com.aerofs.daemon.core.polaris.api.LocationStatusBatch;
import com.aerofs.daemon.core.polaris.api.LocationStatusBatchOperation;
import com.aerofs.daemon.core.polaris.api.LocationStatusBatchResult;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase.RemoteContent;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.ContentHash;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.slf4j.Logger;

import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aerofs.proto.PathStatus.PBPathStatus.Sync.IN_SYNC;
import static com.aerofs.proto.PathStatus.PBPathStatus.Sync.UNKNOWN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * This class uses a real SyncStatusPropagator and in-memory DirectoryService as
 * well, in order to test the effects of the poller on the underlying data
 */
public class TestSyncStatusBatchStatusChecker extends AbstractSyncStatusTest {
    int requests;

    @Before
    public void before() throws Exception {
        requests = 0;
        mockPolarisResponses();
    }

    @Test
    public void shouldRetrySubmissionOnInvalidStatusCode() throws Exception {
        AtomicBoolean success = new AtomicBoolean();
        this.statusChecker.submitLocationStatusBatch(new LocationStatusBatch(null),
                new AsyncTaskCallback() {
                    @Override
                    public void onSuccess_(boolean hasMore) {
                        success.set(true);
                    }

                    @Override
                    public void onFailure_(Throwable t) {
                    }
                }, r -> {
                    return false;
                });
        assertTrue(success.get());
        assertEquals(3, requests);
    }

    private void mockPolarisResponses() {
        HttpResponse[] polarisResponses = new HttpResponse[]{polarisResponse(HttpResponseStatus
                .INTERNAL_SERVER_ERROR), polarisResponse(HttpResponseStatus.BAD_REQUEST),
                polarisResponse(HttpResponseStatus.OK)};

        doAnswer(invocation -> {
            Object[] arg = invocation.getArguments();
            try {
                @SuppressWarnings("unchecked")
                Function<HttpResponse, Boolean, Exception> function =
                        (Function<HttpResponse, Boolean, Exception>) arg[2];

                HttpResponse polarisResponse = polarisResponses[requests++];
                function.apply(polarisResponse);

                if (polarisResponse.getStatus().equals(HttpResponseStatus.OK)) {
                    ((AsyncTaskCallback) arg[1]).onSuccess_(false);
                } else {
                    ((AsyncTaskCallback) arg[1]).onFailure_(new Exception());
                }
            } catch (Throwable t) {
                ((AsyncTaskCallback) arg[1]).onFailure_(t);
            }
            return null;
        }).when(polarisClient).send(any(), any(AsyncTaskCallback.class), any());
    }

    private HttpResponse polarisResponse(HttpResponseStatus status) {
        HttpResponse polarisResponse = mock(HttpResponse.class);
        doReturn(status).when(polarisResponse).getStatus();
        doReturn(ChannelBuffers.copiedBuffer(new Gson()
                        .toJson(new LocationStatusBatchResult(Lists.newArrayList(true, true))),
                Charset.defaultCharset()))
                .when(polarisResponse).getContent();
        return polarisResponse;
    }
}
