package com.aerofs.polaris.api.batch;

import com.aerofs.polaris.api.operation.Operation;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public final class BatchOperation {

    @NotNull
    @Size(min = 1)
    public String oid;

    @NotNull
    public Operation operation;

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BatchOperation other = (BatchOperation) o;
        return oid.equals(other.oid) && operation.equals(other.operation);
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
                .add("update", operation)
                .toString();
    }
}
