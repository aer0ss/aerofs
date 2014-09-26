package com.aerofs.polaris.api.operation;

import com.aerofs.polaris.api.ObjectType;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public final class InsertChild extends Operation {

    @NotNull
    public String child;

    @NotNull
    public ObjectType childObjectType;

    @NotNull
    public String childName;

    public InsertChild() {
        super(OperationType.INSERT_CHILD);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InsertChild other = (InsertChild) o;
        return type == other.type && child.equals(other.child) && childObjectType == other.childObjectType && childName.equals(other.childName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, child, childObjectType, childName);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("type", type)
                .add("child", child)
                .add("childObjectType", childObjectType)
                .add("childName", childName)
                .toString();
    }
}
