package com.aerofs.overload;

import com.google.common.base.Objects;
import io.netty.handler.codec.http.FullHttpRequest;

import javax.annotation.Nullable;

final class Submitted {

    private final FullHttpRequest request;

    @Nullable
    private final SubmittedRequestCallback completionCallback;

    Submitted(FullHttpRequest request, @Nullable SubmittedRequestCallback completionCallback) {
        this.request = request;
        this.completionCallback = completionCallback;
    }

    FullHttpRequest getRequest() {
        return request;
    }

    @Nullable
    SubmittedRequestCallback getCompletionCallback() {
        return completionCallback;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Submitted other = (Submitted) o;
        return Objects.equal(request, other.request) && Objects.equal(completionCallback, other.completionCallback);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(request, completionCallback);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("request", request)
                .add("completionCallback", completionCallback)
                .toString();
    }
}
