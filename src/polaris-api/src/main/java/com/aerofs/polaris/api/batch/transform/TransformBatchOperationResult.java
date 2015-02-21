package com.aerofs.polaris.api.batch.transform;

import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.operation.Updated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import java.util.List;

public final class TransformBatchOperationResult {

    public final boolean successful;

    public final @Nullable List<Updated> updated;

    public final @Nullable PolarisError errorCode;

    public final @Nullable String errorMessage;

    public TransformBatchOperationResult(List<Updated> updated) {
        this(true, updated, null, null);
    }

    public TransformBatchOperationResult(PolarisError errorCode, String errorMessage) {
        this(false, null, errorCode, errorMessage);
    }

    @JsonCreator
    private TransformBatchOperationResult(
            @JsonProperty("successful") boolean successful,
            @JsonProperty("updated") @Nullable List<Updated> updated,
            @JsonProperty("error_code") @Nullable PolarisError errorCode,
            @JsonProperty("error_message") @Nullable String errorMessage) {
        this.successful = successful;
        this.updated = updated;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransformBatchOperationResult other = (TransformBatchOperationResult) o;

        return successful == other.successful
                && Objects.equal(updated, other.updated)
                && Objects.equal(errorCode, other.errorCode)
                && Objects.equal(errorMessage, other.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(successful, updated, errorCode, errorMessage);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("successful", successful)
                .add("updated", updated)
                .add("errorCode", errorCode)
                .add("errorMessage", errorMessage)
                .toString();
    }
}
