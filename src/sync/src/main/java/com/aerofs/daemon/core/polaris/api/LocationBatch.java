package com.aerofs.daemon.core.polaris.api;


import java.util.Collection;

public final class LocationBatch {

    public final Collection<LocationBatchOperation> operations;

    public LocationBatch(Collection<LocationBatchOperation> operations) {
        this.operations = operations;
    }
}
