package com.aerofs.polaris.api.batch.location;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

public final class LocationBatchResult {

    @NotNull
    @Size(min = 1)
    @Valid
    public final List<LocationBatchOperationResult> results;

    @JsonCreator
    public LocationBatchResult(@JsonProperty("results") List<LocationBatchOperationResult> results) {
        this.results = results;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocationBatchResult other = (LocationBatchResult) o;
        return Objects.equal(results, other.results);
    }

    @Override
    public int hashCode() {
        return results.hashCode();
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("results", results)
                .toString();
    }
}
