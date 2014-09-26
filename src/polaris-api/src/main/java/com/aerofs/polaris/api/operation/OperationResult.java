package com.aerofs.polaris.api.operation;

import com.aerofs.polaris.api.LogicalObject;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import java.util.List;

public final class OperationResult {

    public final List<LogicalObject> updated;

    public OperationResult(List<LogicalObject> updated) {
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
