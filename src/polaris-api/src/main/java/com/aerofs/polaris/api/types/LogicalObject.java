package com.aerofs.polaris.api.types;

import com.aerofs.ids.UniqueID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public final class LogicalObject {

    @NotNull
    public final UniqueID root;

    @NotNull
    public final UniqueID oid;

    @Min(0)
    public final long version;

    @NotNull
    public final ObjectType objectType;

    @JsonCreator
    public LogicalObject(
            @JsonProperty("root") UniqueID root,
            @JsonProperty("oid") UniqueID oid,
            @JsonProperty("version") long version,
            @JsonProperty("object_type") ObjectType objectType) {
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
        return Objects.equal(root, other.root)
                && Objects.equal(oid, other.oid)
                && version == other.version
                && Objects.equal(objectType, other.objectType);
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
