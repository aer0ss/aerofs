package com.aerofs.overload;

import io.netty.handler.codec.http.FullHttpResponse;

interface SubmittedRequestCallback {

    void onWriteSucceeded();

    void onResponseReceived(FullHttpResponse response);

    void onFailure(Throwable cause);
}
