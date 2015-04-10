package com.aerofs.polaris.dao.types;

import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.types.LogicalObject;
import com.aerofs.polaris.api.types.ObjectType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Objects;

import javax.annotation.Nullable;

public class LockableLogicalObject extends LogicalObject {

    @JsonIgnore
    public final boolean locked;

    public LockableLogicalObject(UniqueID store, UniqueID oid, long version, ObjectType objectType, boolean locked) {
        super(store, oid, version, objectType);
        this.locked = locked;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return super.equals(o) && locked == ((LockableLogicalObject) o).locked;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(store, oid, version, objectType, locked);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("store", store)
                .add("oid", oid)
                .add("version", version)
                .add("objectType", objectType)
                .add("locked", locked)
                .toString();
    }
}
