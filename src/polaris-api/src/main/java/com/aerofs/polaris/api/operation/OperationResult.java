package com.aerofs.polaris.api.operation;

import com.aerofs.polaris.api.Updated;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

public final class OperationResult {

    @NotNull
    @Size(min = 1)
    public final List<Updated> updated;

    public OperationResult(List<Updated> updated) {
        this.updated = updated;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OperationResult other = (OperationResult) o;
        return updated.equals(other.updated);
    }

    @Override
    public int hashCode() {
        return updated.hashCode();
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("updated", updated)
                .toString();
    }
}
