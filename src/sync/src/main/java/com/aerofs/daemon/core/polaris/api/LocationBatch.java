package com.aerofs.daemon.core.polaris.api;

import java.util.List;

public final class LocationBatch {

    public final String sid;
    public final List<LocationBatchOperation> available;

    public LocationBatch(String sid, List<LocationBatchOperation> available) {
        this.sid = sid;
        this.available = available;
    }
}
