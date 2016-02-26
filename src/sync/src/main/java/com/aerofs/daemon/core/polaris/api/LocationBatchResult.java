package com.aerofs.daemon.core.polaris.api;


import java.util.List;

public final class LocationBatchResult {

    public final List<LocationBatchOperationResult> results;

    public LocationBatchResult(List<LocationBatchOperationResult> results) {
        this.results = results;
    }
}
