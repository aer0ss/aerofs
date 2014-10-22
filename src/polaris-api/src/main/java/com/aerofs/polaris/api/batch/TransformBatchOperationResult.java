package com.aerofs.polaris.api.batch;

import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.operation.Updated;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import java.util.List;

public final class TransformBatchOperationResult {

    public boolean successful;

    @Nullable
    public List<Updated> updated;

    @Nullable
    public PolarisError errorCode;

    @Nullable
    public String errorMessage;

    /**
     * For Jackson use only - do not use directly.
     */
    @SuppressWarnings("unused")
    private TransformBatchOperationResult() { }

    public TransformBatchOperationResult(List<Updated> updated) {
        this.successful = true;
        this.updated = updated;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public TransformBatchOperationResult(PolarisError errorCode, String errorMessage) {
        this.successful = false;
        this.updated = null;
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
