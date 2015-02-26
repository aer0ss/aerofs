package com.aerofs.polaris.api.batch.location;

import com.aerofs.polaris.api.batch.BatchError;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.google.common.base.Objects;

import javax.annotation.Nullable;

public final class LocationBatchOperationResult {

    public final boolean successful;

    public final @Nullable BatchError error;

    public LocationBatchOperationResult() {
        this(true, null);
    }

    public LocationBatchOperationResult(BatchError error) {
        this(false, error);
    }

    @JsonCreator
    private LocationBatchOperationResult( @JsonProperty("successful") boolean successful, @JsonUnwrapped @Nullable BatchError error) {
        this.successful = successful;
        this.error = error;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocationBatchOperationResult other = (LocationBatchOperationResult) o;
        return successful == other.successful && Objects.equal(error, other.error);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(successful, error);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("successful", successful)
                .add("error", error)
                .toString();
    }
}
