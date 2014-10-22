package com.aerofs.polaris.api.operation;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public final class RemoveChild extends Operation {

    @NotNull
    @Size(min = 1)
    public String child;

    /**
     * For Jackson use only - do not use directly.
     */
    @SuppressWarnings("unused")
    private RemoveChild() {
        super(OperationType.REMOVE_CHILD);
    }

    public RemoveChild(String child) {
        super(OperationType.REMOVE_CHILD);
        this.child = child;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RemoveChild other = (RemoveChild) o;
        return type == other.type && Objects.equal(child, other.child);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, child);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("type", type)
                .add("child", child)
                .toString();
    }
}
