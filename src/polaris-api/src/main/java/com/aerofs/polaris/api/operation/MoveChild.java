package com.aerofs.polaris.api.operation;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public final class MoveChild extends Operation {

    @NotNull
    @Size(min = 1)
    public String child;

    @NotNull
    @Size(min = 1)
    public String newParent;

    @NotNull
    @Size(min = 1)
    public String newChildName;

    /**
     * For Jackson use only - do not use directly.
     */
    @SuppressWarnings("unused")
    private MoveChild() {
        super(OperationType.MOVE_CHILD);
    }

    public MoveChild(String child, String newParent, String newChildName) {
        super(OperationType.MOVE_CHILD);

        this.child = child;
        this.newParent = newParent;
        this.newChildName = newChildName;
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
