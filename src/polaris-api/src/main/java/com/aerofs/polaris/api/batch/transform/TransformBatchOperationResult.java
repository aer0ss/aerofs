package com.aerofs.polaris.api.batch.transform;

import com.aerofs.polaris.api.batch.BatchError;
import com.aerofs.polaris.api.operation.Updated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import java.util.List;

public final class TransformBatchOperationResult {

    public final boolean successful;

    public final @Nullable List<Updated> updated;

    public final @Nullable BatchError error;

    public TransformBatchOperationResult(List<Updated> updated) {
        this(true, updated, null);
    }

    public TransformBatchOperationResult(BatchError error) {
        this(false, null, error);
    }

    @JsonCreator
    private TransformBatchOperationResult(
            @JsonProperty("successful") boolean successful,
            @JsonProperty("updated") @Nullable List<Updated> updated,
            @JsonUnwrapped @Nullable BatchError error) {
        this.successful = successful;
        this.updated = updated;
        this.error = error;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransformBatchOperationResult other = (TransformBatchOperationResult) o;
        return successful == other.successful && Objects.equal(updated, other.updated) && Objects.equal(error, other.error);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(successful, updated, error);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("successful", successful)
                .add("updated", updated)
                .add("error", error)
                .toString();
    }
}
