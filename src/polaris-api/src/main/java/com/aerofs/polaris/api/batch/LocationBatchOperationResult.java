package com.aerofs.polaris.api.batch;

import com.aerofs.polaris.api.PolarisError;
import com.google.common.base.Objects;

import javax.annotation.Nullable;

public final class LocationBatchOperationResult {

    public boolean successful;

    @Nullable
    public PolarisError errorCode;

    @Nullable
    public String errorMessage;

    public LocationBatchOperationResult() {
        this.successful = true;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public LocationBatchOperationResult(PolarisError errorCode, String errorMessage) {
        this.successful = false;
        this.errorCode = errorCode;
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
