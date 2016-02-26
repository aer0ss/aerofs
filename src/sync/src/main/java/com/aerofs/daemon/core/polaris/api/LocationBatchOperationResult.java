package com.aerofs.daemon.core.polaris.api;

import javax.annotation.Nullable;

public final class LocationBatchOperationResult {

    public final boolean successful;

    public final @Nullable BatchError error;

    public LocationBatchOperationResult() {
        this(true, null);
    }

    public LocationBatchOperationResult(BatchError error) {
        this(false, error);
    }

    private LocationBatchOperationResult(boolean successful, @Nullable BatchError error) {
        this.successful = successful;
        this.error = error;
    }
}
