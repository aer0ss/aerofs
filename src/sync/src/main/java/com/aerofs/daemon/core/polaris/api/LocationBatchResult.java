package com.aerofs.daemon.core.polaris.api;


import java.util.List;

public final class LocationBatchResult {
    public final List<Boolean> results;

    public LocationBatchResult(List<Boolean> results) {
        this.results = results;
    }
}
