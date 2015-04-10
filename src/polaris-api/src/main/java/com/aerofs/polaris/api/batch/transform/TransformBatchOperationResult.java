package com.aerofs.polaris.api.batch.transform;

import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.batch.BatchError;
import com.aerofs.polaris.api.operation.OperationResult;
import com.aerofs.polaris.api.operation.Updated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import java.util.List;

public final class TransformBatchOperationResult {

    public final boolean successful;

    public final @JsonUnwrapped @Nullable OperationResult operationResult;

    public final @JsonUnwrapped @Nullable BatchError error;

    public TransformBatchOperationResult(OperationResult result) {
        this(true, result.updated, result.jobID, null, null);
    }

    public TransformBatchOperationResult(BatchError error) {
        this(false, null, null, error.errorCode, error.errorMessage);
    }

    // DO NOT REMOVE: these 2 methods are needed because of a bug in jackson versions < 2.5, it will call the jsoncreator constructor
    // and erroneously try to populate the operationResult and error fields directly
    private void setOperationResult(OperationResult result) {
        // do nothing
    }

    private void setError(BatchError error) {
        // do nothing
    }

    @JsonCreator
    private TransformBatchOperationResult(
            @JsonProperty("successful") boolean successful,
            @Nullable @JsonProperty("updated") List<Updated> updated,
            @Nullable @JsonProperty("job_id") UniqueID jobID,
            @Nullable @JsonProperty("error_code") PolarisError errorCode,
            @Nullable @JsonProperty("error_message") String errorMessage)
    {
        this.successful = successful;
        this.operationResult = successful ? new OperationResult(updated, jobID) : null;
        this.error = successful ? null : new BatchError(errorCode, errorMessage);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransformBatchOperationResult other = (TransformBatchOperationResult) o;
        return successful == other.successful && Objects.equal(operationResult, other.operationResult) && Objects.equal(error, other.error);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(successful, operationResult, error);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("successful", successful)
                .add("result", operationResult)
                .add("error", error)
                .toString();
    }
}
