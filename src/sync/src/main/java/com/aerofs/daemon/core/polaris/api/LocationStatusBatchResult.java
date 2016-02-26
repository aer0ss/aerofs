package com.aerofs.daemon.core.polaris.api;

import java.util.List;

public final class LocationStatusBatchResult
{
    public final List<Boolean> results;

    public LocationStatusBatchResult(List<Boolean> results) {
        this.results = results;
    }
}
