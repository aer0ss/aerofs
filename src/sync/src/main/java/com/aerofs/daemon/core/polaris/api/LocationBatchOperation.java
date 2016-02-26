package com.aerofs.daemon.core.polaris.api;

public final class LocationBatchOperation {

    public final String oid;

    public final long version;

    public final String did;

    public final LocationUpdateType locationUpdateType;

    public LocationBatchOperation(String oid, long version, String did, LocationUpdateType locationUpdateType) {
        this.oid = oid;
        this.version = version;
        this.did = did;
        this.locationUpdateType = locationUpdateType;
    }
}
