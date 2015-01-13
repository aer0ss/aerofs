package com.aerofs.polaris.api.operation;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@SuppressWarnings("unused")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
public final class RemoveChild extends Operation {

    @NotNull
    @Size(min = 1)
    private String child;

    public RemoveChild(String child) {
        super(OperationType.REMOVE_CHILD);
        this.child = child;
    }

    private RemoveChild() { super(OperationType.REMOVE_CHILD); }

    public String getChild() {
        return child;
    }

    private void setChild(String child) {
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
