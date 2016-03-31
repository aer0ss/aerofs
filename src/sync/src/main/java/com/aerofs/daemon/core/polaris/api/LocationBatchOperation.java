package com.aerofs.daemon.core.polaris.api;

public final class LocationBatchOperation {
    public final String oid;

    public final long version;

    public LocationBatchOperation(String oid, long version) {
        this.oid = oid;
        this.version = version;
    }
}
