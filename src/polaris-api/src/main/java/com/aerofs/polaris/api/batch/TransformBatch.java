package com.aerofs.polaris.api.batch;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

public final class TransformBatch {

    @NotNull
    @Size(min = 1)
    @Valid
    public List<TransformBatchOperation> operations;

    /**
     * For Jackson use only - do not use directly.
     */
    @SuppressWarnings("unused")
    private TransformBatch() {}

    public TransformBatch(List<TransformBatchOperation> operations) {
        this.operations = operations;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransformBatch other = (TransformBatch) o;
        return Objects.equal(operations, other.operations);
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
