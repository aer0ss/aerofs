package com.aerofs.polaris.api.operation;

import com.aerofs.polaris.api.types.ObjectType;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public final class InsertChild extends Operation {

    @NotNull
    @Size(min = 1)
    public String child;

    @Nullable
    public ObjectType childObjectType;

    @NotNull
    @Size(min = 1)
    public String childName;

    /**
     * For Jackson use only - do not use directly.
     */
    @SuppressWarnings("unused")
    private InsertChild() {
        super(OperationType.INSERT_CHILD);
    }

    public InsertChild(String child, @Nullable ObjectType childObjectType, String childName) {
        super(OperationType.INSERT_CHILD);
        this.child = child;
        this.childObjectType = childObjectType;
        this.childName = childName;
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
