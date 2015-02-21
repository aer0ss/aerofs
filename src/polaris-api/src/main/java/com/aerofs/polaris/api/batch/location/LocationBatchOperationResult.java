package com.aerofs.polaris.api.batch.location;

import com.aerofs.polaris.api.PolarisError;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;

public final class LocationBatchOperationResult {

    public final boolean successful;

    public final @Nullable PolarisError errorCode;

    public final @Nullable String errorMessage;

    public LocationBatchOperationResult(PolarisError errorCode, String errorMessage) {
        this(false, errorCode, errorMessage);
    }

    public LocationBatchOperationResult() {
        this(true, null, null);
    }

    @JsonCreator
    private LocationBatchOperationResult(
            @JsonProperty("successful") boolean successful,
            @JsonProperty("error_code") @Nullable PolarisError errorCode,
            @JsonProperty("error_message") @Nullable String errorMessage) {
        this.successful = successful;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocationBatchOperationResult other = (LocationBatchOperationResult) o;
        return successful == other.successful
                && Objects.equal(errorCode, other.errorCode)
                && Objects.equal(errorMessage, other.errorMessage);
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
