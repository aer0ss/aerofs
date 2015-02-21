package com.aerofs.polaris.api.batch.location;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

public final class LocationBatch {

    @NotNull
    @Size(min = 1)
    @Valid
    public final List<LocationBatchOperation> operations;

    @JsonCreator
    public LocationBatch(@JsonProperty("operations") List<LocationBatchOperation> operations) {
        this.operations = operations;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocationBatch other = (LocationBatch) o;
        return Objects.equal(operations, other.operations);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(operations);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("operations", operations)
                .toString();
    }
}
