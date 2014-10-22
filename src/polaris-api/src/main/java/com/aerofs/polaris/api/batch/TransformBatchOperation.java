package com.aerofs.polaris.api.batch;

import com.aerofs.polaris.api.operation.Operation;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public final class TransformBatchOperation {

    @NotNull
    @Size(min = 1)
    public String oid;

    @NotNull
    @Valid
    public Operation operation;

    /**
     * For Jackson use only - do not use directly.
     */
    @SuppressWarnings("unused")
    private TransformBatchOperation() { }

    public TransformBatchOperation(String oid, Operation operation) {
        this.oid = oid;
        this.operation = operation;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransformBatchOperation other = (TransformBatchOperation) o;
        return Objects.equal(oid, other.oid) && Objects.equal(operation, other.operation);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(oid, operation);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("oid", oid)
                .add("operation", operation)
                .toString();
    }
}
