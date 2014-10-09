package com.aerofs.polaris.api.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public final class LogicalObject {

    @JsonIgnore // property exists, but we won't serialize it
    @NotNull
    @Size(min = 1)
    public String root;

    @NotNull
    @Size(min = 1)
    public String oid;

    @Min(0)
    public long version;

    @NotNull
    public ObjectType objectType;

    /**
     * For Jackson use only - do not use directly.
     */
    @SuppressWarnings("unused")
    private LogicalObject() { }

    public LogicalObject(String root, String oid, long version, ObjectType objectType) {
        this.root = root;
        this.oid = oid;
        this.version = version;
        this.objectType = objectType;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LogicalObject other = (LogicalObject) o;
        return root.equals(other.root) && oid.equals(other.oid) && version == other.version && objectType.equals(other.objectType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(root, oid, version, objectType);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("root", root)
                .add("oid", oid)
                .add("version", version)
                .add("objectType", objectType)
                .toString();
    }
}
