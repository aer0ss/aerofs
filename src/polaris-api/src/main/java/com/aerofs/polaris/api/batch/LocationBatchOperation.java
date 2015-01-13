package com.aerofs.polaris.api.batch;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@SuppressWarnings("unused")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
public final class LocationBatchOperation {

    public static enum LocationUpdateType {
        INSERT,
        REMOVE,
    }

    @NotNull
    @Size(min = 1)
    private String oid;

    @Min(0)
    private long version;

    @NotNull
    @Size(min = 1)
    private String did;

    @NotNull
    private LocationUpdateType locationUpdateType;

    public LocationBatchOperation(String oid, long version, String did, LocationUpdateType locationUpdateType) {
        this.oid = oid;
        this.version = version;
        this.did = did;
        this.locationUpdateType = locationUpdateType;
    }

    private LocationBatchOperation() { }

    public String getOid() {
        return oid;
    }

    private void setOid(String oid) {
        this.oid = oid;
    }

    public long getVersion() {
        return version;
    }

    private void setVersion(long version) {
        this.version = version;
    }

    public String getDid() {
        return did;
    }

    private void setDid(String did) {
        this.did = did;
    }

    public LocationUpdateType getLocationUpdateType() {
        return locationUpdateType;
    }

    private void setLocationUpdateType(LocationUpdateType locationUpdateType) {
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
