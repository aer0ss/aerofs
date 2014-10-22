package com.aerofs.polaris.api.batch;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public final class LocationBatchOperation {

    public static enum LocationUpdateType {
        INSERT,
        REMOVE,
    }

    @NotNull
    @Size(min = 1)
    public String oid;

    @Min(0)
    public long version;

    @NotNull
    @Size(min = 1)
    public String did;

    @NotNull
    public LocationUpdateType locationUpdateType;

    /**
     * For Jackson use only - do not use directly.
     */
    @SuppressWarnings("unused")
    private LocationBatchOperation() { }

    public LocationBatchOperation(String oid, long version, String did, LocationUpdateType locationUpdateType) {
        this.oid = oid;
        this.version = version;
        this.did = did;
        this.locationUpdateType = locationUpdateType;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocationBatchOperation other = (LocationBatchOperation) o;
        return Objects.equal(oid, other.oid) && version == other.version && Objects.equal(did, other.did) && Objects.equal(locationUpdateType, other.locationUpdateType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(oid, version, did, locationUpdateType);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("oid", oid)
                .add("version", version)
                .add("did", did)
                .add("locationUpdateType", locationUpdateType)
                .toString();
    }
}
