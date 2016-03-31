package com.aerofs.daemon.core.status;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.AsyncHttpClient.Function;
import com.aerofs.daemon.core.net.DeviceToUserMapper;
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
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.slf4j.Logger;

import java.nio.charset.Charset;

import static com.aerofs.proto.PathStatus.PBPathStatus.Sync.IN_SYNC;
import static com.aerofs.proto.PathStatus.PBPathStatus.Sync.UNKNOWN;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * This class uses a real SyncStatusPropagator and in-memory DirectoryService as
 * well, in order to test the effects of the poller on the underlying data
 */
public class TestSyncStatusContentVersionListener extends AbstractSyncStatusTest
{
    private static final Logger l = Loggers.getLogger(TestSyncStatusContentVersionListener.class);
    @Mock RemoteContentDatabase remoteContentDatabase;
    @Mock DeviceToUserMapper d2u;

    SyncStatusContentVersionListener contentVersionListener;

    DID storageAgent = DID.generate();
    DID other = DID.generate();

    @Before
    public void before() throws Exception {
        l.trace("before");

        doReturn(UserID.UNKNOWN_TEAM_SERVER).when(d2u).getUserIDForDIDNullable_(storageAgent);
        doReturn(UserID.UNKNOWN).when(d2u).getUserIDForDIDNullable_(other);

        contentVersionListener = new SyncStatusContentVersionListener(propagator, syncStatusRequests,
                remoteContentDatabase, d2u, statusChecker, transManager);

        l.trace("end before");
    }

    @Test
    public void shouldSetOutOfSyncWhenSetToOldVersion() throws Exception {
        doReturn(new RemoteContent(3, storageAgent, ContentHash.EMPTY, 0)).when(remoteContentDatabase)
                .getMaxRow_(baz.sidx(), baz.oid());

        try (Trans t = transManager.begin_()) {
            contentVersionListener.onSetVersion_(baz.sidx(), baz.oid(), 2, t);
            t.commit_();
        }
        assertEquals(UNKNOWN, propagator.getSync_(baz));
    }

    @Test
    public void shouldSetOutOfSyncWhenSetToNewVersion() throws Exception {
        doReturn(new RemoteContent(3, storageAgent, ContentHash.EMPTY, 0)).when(remoteContentDatabase)
                .getMaxRow_(baz.sidx(), baz.oid());

        try (Trans t = transManager.begin_()) {
            contentVersionListener.onSetVersion_(baz.sidx(), baz.oid(), 4, t);
            t.commit_();
        }
        assertEquals(UNKNOWN, propagator.getSync_(baz));
    }

    @Test
    public void shouldSetInSyncWhenVersionEqualAndFromStorageAgent() throws Exception {
        doReturn(new RemoteContent(3, storageAgent, ContentHash.EMPTY, 0)).when(remoteContentDatabase)
                .getMaxRow_(baz.sidx(), baz.oid());

        try (Trans t = transManager.begin_()) {
            contentVersionListener.onSetVersion_(baz.sidx(), baz.oid(), 3, t);
            t.commit_();
        }
        assertEquals(IN_SYNC, propagator.getSync_(baz));
    }

    @Test
    public void shouldQueryPolarisForTwoFiles_InSync_WhenVersionEqualAndNotFromStorageAgent()
            throws Exception {
        doReturn(new RemoteContent(3, other, ContentHash.EMPTY, 0)).when(remoteContentDatabase)
                .getMaxRow_(baz.sidx(), baz.oid());
        doReturn(new RemoteContent(3, other, ContentHash.EMPTY, 0)).when(remoteContentDatabase)
                .getMaxRow_(qux.sidx(), qux.oid());

        mockPolarisResponse(syncedLocationBatchResult());

        try (Trans t = transManager.begin_()) {
            // set out of sync to make sure that the listener sets to in sync
            propagator.updateSyncStatus_(baz, false, t);
            propagator.updateSyncStatus_(qux, false, t);

            contentVersionListener.onSetVersion_(baz.sidx(), baz.oid(), 3, t);
            contentVersionListener.onSetVersion_(qux.sidx(), qux.oid(), 3, t);
            assertEquals(UNKNOWN, propagator.getSync_(qux, t));
            assertEquals(UNKNOWN, propagator.getSync_(baz, t));
            statusChecker.completed = false;
            t.commit_();
        }
        while (!statusChecker.completed);

        assertEquals(IN_SYNC, propagator.getSync_(qux));
        assertEquals(IN_SYNC, propagator.getSync_(baz));
    }

    @Test
    public void shouldQueryPolarisForTwoFiles_OutOfSync_WhenVersionEqualAndNotFromStorageAgent()
            throws Exception {
        doReturn(new RemoteContent(3, other, ContentHash.EMPTY, 0)).when(remoteContentDatabase)
                .getMaxRow_(baz.sidx(), baz.oid());
        doReturn(new RemoteContent(3, other, ContentHash.EMPTY, 0)).when(remoteContentDatabase)
                .getMaxRow_(qux.sidx(), qux.oid());

        mockPolarisResponse(notSyncedLocationBatchResult());

        try (Trans t = transManager.begin_()) {
            contentVersionListener.onSetVersion_(baz.sidx(), baz.oid(), 3, t);
            contentVersionListener.onSetVersion_(qux.sidx(), qux.oid(), 3, t);
            assertEquals(UNKNOWN, propagator.getSync_(qux, t));
            assertEquals(UNKNOWN, propagator.getSync_(baz, t));
            statusChecker.completed = false;
            t.commit_();
        }
        while (!statusChecker.completed);

        assertEquals(UNKNOWN, propagator.getSync_(qux));
        assertEquals(UNKNOWN, propagator.getSync_(baz));
    }

    private void mockPolarisResponse(LocationStatusBatchResult result) {
        HttpResponse polarisResponse = polarisResponse(result);

        doAnswer(invocation -> {
            Object[] arg = invocation.getArguments();
            try {
                @SuppressWarnings("unchecked")
                Function<HttpResponse, Boolean, Exception> function = (Function<HttpResponse, Boolean, Exception>) arg[2];

                function.apply(polarisResponse);
                ((AsyncTaskCallback) arg[1]).onSuccess_(false);
            } catch (Throwable t) {
                ((AsyncTaskCallback) arg[1]).onFailure_(t);
            }
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
}
