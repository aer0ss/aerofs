package com.aerofs.daemon.core.status;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.AsyncHttpClient.Function;
import com.aerofs.daemon.core.polaris.WaldoAsyncClient;
import com.aerofs.daemon.core.polaris.api.LocationStatusBatch;
import com.aerofs.daemon.core.polaris.api.LocationStatusBatchResult;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.slf4j.Logger;

import javax.inject.Inject;

import static com.aerofs.base.BaseUtil.string2utf;
import static com.aerofs.daemon.core.polaris.GsonUtil.GSON;

public class SyncStatusBatchStatusChecker
{
    private final static Logger l = Loggers.getLogger(SyncStatusBatchStatusChecker.class);

    private final WaldoAsyncClient _waldoClient;

    @Inject
    public SyncStatusBatchStatusChecker(WaldoAsyncClient.Factory factWaldo) {
        _waldoClient = factWaldo.create();
    }

    public void submitLocationStatusBatch(LocationStatusBatch locationStatusBatch,
            AsyncTaskCallback callback,
            Function<LocationStatusBatchResult, Boolean, Exception> responseFunction) {
        byte[] content = string2utf(GSON.toJson(locationStatusBatch));

        new RetryableStatusCheck("/status", content, callback,
                response -> parseResponse(response, locationStatusBatch, responseFunction)).send();
    }

    private Boolean parseResponse(HttpResponse response, LocationStatusBatch locationStatusBatch,
            Function<LocationStatusBatchResult, Boolean, Exception> responseFunction) throws Exception {
        int code = response.getStatus().getCode();
        if (code >= HttpResponseStatus.OK.getCode()
                && code < HttpResponseStatus.MULTIPLE_CHOICES.getCode()) {
            String content = response.getContent().toString(BaseUtil.CHARSET_UTF);
            l.debug("batch status response: {}", content);
            LocationStatusBatchResult batchResult = GSON.fromJson(content,
                    LocationStatusBatchResult.class);

            return responseFunction.apply(batchResult);
        } else {
            if (response.getStatus().getCode() >= 500) {
                throw new ExRetryLater(response.getStatus().getReasonPhrase());
            }
            throw new ExProtocolError(response.getStatus().getReasonPhrase());
        }
    }

    private class RetryableStatusCheck implements AsyncTaskCallback
    {
        String uri;
        byte[] content;
        AsyncTaskCallback callback;
        Function<HttpResponse, Boolean, Exception> function;
        int retries = 10;

        public RetryableStatusCheck(String uri, byte[] content, AsyncTaskCallback callback,
                Function<HttpResponse, Boolean, Exception> function) {
            this.uri = uri;
            this.content = content;
            this.callback = callback;
            this.function = function;
        }

        @Override
        public void onSuccess_(boolean hasMore) {
            callback.onSuccess_(hasMore);
        }

        @Override
        public void onFailure_(Throwable t) {
            if (retries == 0) {
                callback.onFailure_(t);
                return;
            }

            retries--;

            this.send();
        }

        public void send() {
            DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                    uri);
            request.headers().add(HttpHeaders.Names.CONTENT_TYPE, "application/json");
            request.headers().add(Names.CONTENT_LENGTH, content.length);
            request.setContent(ChannelBuffers.wrappedBuffer(content));
            _waldoClient.send(request, this, function);
        }
    }
}
