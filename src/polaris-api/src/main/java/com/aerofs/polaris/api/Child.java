package com.aerofs.polaris.api;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

// FIXME (AG): combine with LogicalObject if possible
public final class Child {

    @NotNull
    public final String oid;

    @NotNull
    public final String name;

    @NotNull
    public final ObjectType objectType;

    public Child(String oid, String name, ObjectType objectType) {
        this.oid = oid;
        this.name = name;
        this.objectType = objectType;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Child other = (Child) o;
        return oid.equals(other.oid) && name.equals(other.name) && objectType.equals(other.objectType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(oid, name, objectType);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("oid", oid)
                .add("name", name)
                .add("objectType", objectType)
                .toString();
    }
}
