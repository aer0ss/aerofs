package com.aerofs.polaris.api.batch;

import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.operation.Updated;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("unused")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
public final class TransformBatchOperationResult {

    private boolean successful;

    @Nullable
    private List<Updated> updated;

    @Nullable
    private PolarisError errorCode;

    @Nullable
    private String errorMessage;


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

    private TransformBatchOperationResult() { }

    public boolean isSuccessful() {
        return successful;
    }

    private void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    @Nullable
    public List<Updated> getUpdated() {
        return updated;
    }

    private void setUpdated(@Nullable List<Updated> updated) {
        this.updated = updated;
    }

    @Nullable
    public PolarisError getErrorCode() {
        return errorCode;
    }

    private void setErrorCode(@Nullable PolarisError errorCode) {
        this.errorCode = errorCode;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    private void setErrorMessage(@Nullable String errorMessage) {
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
