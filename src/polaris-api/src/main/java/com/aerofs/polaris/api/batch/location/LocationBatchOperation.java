package com.aerofs.polaris.api.batch.location;

import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public final class LocationBatchOperation {

    @NotNull
    public final OID oid;

    @Min(0)
    public final long version;

    @NotNull
    public final DID did;

    @NotNull
    public final LocationUpdateType locationUpdateType;

    @JsonCreator
    public LocationBatchOperation(
            @JsonProperty("oid") OID oid,
            @JsonProperty("version") long version,
            @JsonProperty("did") DID did,
            @JsonProperty("location_update_type") LocationUpdateType locationUpdateType) {
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
        return Objects.equal(oid, other.oid)
                && version == other.version
                && Objects.equal(did, other.did)
                && Objects.equal(locationUpdateType, other.locationUpdateType);
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
