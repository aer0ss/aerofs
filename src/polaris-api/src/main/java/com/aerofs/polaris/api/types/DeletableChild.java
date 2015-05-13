package com.aerofs.polaris.api.types;

import com.aerofs.ids.UniqueID;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import java.util.Arrays;

public class DeletableChild extends Child
{
    public final boolean deleted;

    public DeletableChild(UniqueID oid, ObjectType objectType, byte[] name, boolean deleted)
    {
        super(oid, objectType, name);
        this.deleted = deleted;
    }

    public DeletableChild(UniqueID oid, ObjectType objectType, String name, boolean deleted)
    {
        super(oid, objectType, name);
        this.deleted = deleted;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DeletableChild other = (DeletableChild) o;
        return Objects.equal(oid, other.oid)
                && Arrays.equals(name, other.name)
                && Objects.equal(objectType, other.objectType)
                && Objects.equal(deleted, other.deleted);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(oid, name, objectType, deleted);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("oid", oid)
                .add("name", name)
                .add("objectType", objectType)
                .add("deleted", deleted)
                .toString();
    }
}
