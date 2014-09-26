package com.aerofs.polaris.api.operation;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public final class MoveChild extends Operation {

    @NotNull
    public String child;

    @NotNull
    public String newParent;

    @NotNull
    public String newChildName;

    public MoveChild() {
        super(OperationType.MOVE_CHILD);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MoveChild other = (MoveChild) o;
        return type == other.type && child.equals(other.child) && newParent.equals(other.newParent) && newChildName.equals(other.newChildName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, child, newParent, newChildName);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("child", child)
                .add("newParent", newParent)
                .add("newChildName", newChildName)
                .toString();
    }
}
