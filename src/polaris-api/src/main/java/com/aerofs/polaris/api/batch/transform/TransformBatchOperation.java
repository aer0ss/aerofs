package com.aerofs.polaris.api.batch.transform;

import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.operation.Operation;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public final class TransformBatchOperation {

    @NotNull
    public final UniqueID oid;

    @NotNull
    @Valid
    public final Operation operation;

    @JsonCreator
    public TransformBatchOperation(@JsonProperty("oid") UniqueID oid, @JsonProperty("operation") Operation operation) {
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
