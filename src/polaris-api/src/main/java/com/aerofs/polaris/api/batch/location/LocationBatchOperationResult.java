package com.aerofs.polaris.api.batch.location;

import com.aerofs.polaris.api.PolarisError;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.base.Objects;

import javax.annotation.Nullable;

@SuppressWarnings("unused")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
public final class LocationBatchOperationResult {

    private boolean successful;

    @Nullable
    private PolarisError errorCode;

    @Nullable
    private String errorMessage;

    public LocationBatchOperationResult(PolarisError errorCode, String errorMessage) {
        this.successful = false;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public LocationBatchOperationResult() {
        this.successful = true;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public boolean isSuccessful() {
        return successful;
    }

    private void setSuccessful(boolean successful) {
        this.successful = successful;
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

        LocationBatchOperationResult other = (LocationBatchOperationResult) o;
        return successful == other.successful && Objects.equal(errorCode, other.errorCode) && Objects.equal(errorMessage, other.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(successful, errorCode, errorMessage);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("successful", successful)
                .add("errorCode", errorCode)
                .add("errorMessage", errorMessage)
                .toString();
    }
}
