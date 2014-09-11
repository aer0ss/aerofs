package com.aerofs.polaris.api;

import com.google.common.base.Objects;
import org.hibernate.validator.constraints.NotEmpty;

import javax.annotation.Nullable;
import javax.validation.Valid;
import java.util.List;

public final class BatchUpdate {

    public boolean atomic = false;

    @Valid
    @NotEmpty
    public List<BatchOperation> batchOperations;

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BatchUpdate other = (BatchUpdate) o;
        return atomic == other.atomic && batchOperations.equals(other.batchOperations);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(batchOperations);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("atomic", atomic)
                .add("batchOperations", batchOperations)
                .toString();
    }
}
