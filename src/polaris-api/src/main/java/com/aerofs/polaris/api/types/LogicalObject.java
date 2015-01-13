package com.aerofs.polaris.api.types;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@SuppressWarnings("unused")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
public final class LogicalObject {

    @NotNull
    @Size(min = 1)
    private String root;

    @NotNull
    @Size(min = 1)
    private String oid;

    @Min(0)
    private long version;

    @NotNull
    private ObjectType objectType;

    public LogicalObject(String root, String oid, long version, ObjectType objectType) {
        this.root = root;
        this.oid = oid;
        this.version = version;
        this.objectType = objectType;
    }

    private LogicalObject() { }

    public String getRoot() {
        return root;
    }

    private void setRoot(String root) {
        this.root = root;
    }

    public String getOid() {
        return oid;
    }

    private void setOid(String oid) {
        this.oid = oid;
    }

    public long getVersion() {
        return version;
    }

    private void setVersion(long version) {
        this.version = version;
    }

    public ObjectType getObjectType() {
        return objectType;
    }

    private void setObjectType(ObjectType objectType) {
        this.objectType = objectType;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LogicalObject other = (LogicalObject) o;
        return Objects.equal(root, other.root) && Objects.equal(oid, other.oid) && version == other.version && Objects.equal(objectType, other.objectType);
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
