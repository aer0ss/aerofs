package com.aerofs.polaris.api.operation;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public final class RemoveChild extends Operation {

    @NotNull
    @Size(min = 1)
    public String child;

    public RemoveChild() {
        super(OperationType.REMOVE_CHILD);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RemoveChild other = (RemoveChild) o;
        return type == other.type && child.equals(other.child);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, child);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("child", child)
                .toString();
    }
}
