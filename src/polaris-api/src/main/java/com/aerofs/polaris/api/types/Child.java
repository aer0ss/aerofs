package com.aerofs.polaris.api.types;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public final class Child {

    @NotNull
    @Size(min = 1)
    public String oid;

    @NotNull
    @Size(min = 1)
    public String name;

    @NotNull
    public ObjectType objectType;

    /**
     * For Jackson use only - do not use directly.
     */
    @SuppressWarnings("unused")
    private Child() { }

    public Child(String oid, ObjectType objectType, String name) {
        this.oid = oid;
        this.name = name;
        this.objectType = objectType;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Child other = (Child) o;
        return Objects.equal(oid, other.oid) && Objects.equal(name, other.name) && Objects.equal(objectType, other.objectType);
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
