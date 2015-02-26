package com.aerofs.polaris.api.batch;

import com.aerofs.polaris.api.PolarisError;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;

public final class BatchError {

    public final PolarisError errorCode;

    public final String errorMessage;

    @JsonCreator
    public BatchError(@JsonProperty("error_code") PolarisError errorCode, @JsonProperty("error_message") String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BatchError other = (BatchError) o;
        return Objects.equal(errorCode, other.errorCode) && Objects.equal(errorMessage, other.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(errorCode, errorMessage);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("errorCode", errorCode)
                .add("errorMessage", errorMessage)
                .toString();
    }
}
