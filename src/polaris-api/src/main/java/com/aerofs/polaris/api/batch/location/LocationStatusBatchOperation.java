package com.aerofs.polaris.api.batch.location;

import com.aerofs.ids.OID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class LocationStatusBatchOperation
{
    @NotNull public final OID oid;

    @Min(0) public final long version;

    @JsonCreator
    public LocationStatusBatchOperation(@JsonProperty("oid") OID oid,
            @JsonProperty("version") long version) {
        this.oid = oid;
        this.version = version;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocationStatusBatchOperation other = (LocationStatusBatchOperation) o;
        return Objects.equal(oid, other.oid) && version == other.version;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(oid, version);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("oid", oid).add("version", version).toString();
    }
}
