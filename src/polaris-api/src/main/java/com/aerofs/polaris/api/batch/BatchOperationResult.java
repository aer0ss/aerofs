package com.aerofs.polaris.api.batch;

import com.aerofs.polaris.api.ErrorCode;
import com.aerofs.polaris.api.LogicalObject;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import java.util.List;

public final class BatchOperationResult {

    public final boolean successful;

    @Nullable
    public final List<LogicalObject> logicalObjects;

    @Nullable
    public final ErrorCode errorCode;

    @Nullable
    public final String errorMessage;

    public BatchOperationResult(List<LogicalObject> logicalObjects) {
        this.successful = true;
        this.logicalObjects = logicalObjects;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public BatchOperationResult(ErrorCode errorCode, String errorMessage) {
        this.successful = false;
        this.logicalObjects = null;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BatchOperationResult other = (BatchOperationResult) o;

        return successful == other.successful
                && Objects.equal(logicalObjects, other.logicalObjects)
                && Objects.equal(errorCode, other.errorCode)
                && Objects.equal(errorMessage, other.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(successful, logicalObjects, errorCode, errorMessage);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("successful", successful)
                .add("logicalObjects", logicalObjects)
                .add("errorCode", errorCode)
                .add("errorMessage", errorMessage)
                .toString();
    }
}
