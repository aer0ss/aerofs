package com.aerofs.polaris.api.batch;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

public final class Batch {

    @Valid
    @NotNull
    @Size(min = 1)
    public List<BatchOperation> operations;

    /**
     * For Jackson use only - do not use directly.
     */
    @SuppressWarnings("unused")
    private Batch() {}

    public Batch(List<BatchOperation> operations) {
        this.operations = operations;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Batch other = (Batch) o;
        return operations.equals(other.operations);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(operations);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("operations", operations)
                .toString();
    }
}
