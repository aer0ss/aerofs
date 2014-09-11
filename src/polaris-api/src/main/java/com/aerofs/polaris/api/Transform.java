package com.aerofs.polaris.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Objects;

import javax.annotation.Nullable;

public final class Transform {

    public final long logicalTimestamp;

    @JsonIgnore
    public final String root;

    public final String oid;

    public final ObjectType objectType;

    public final TransformType transformType;

    public final long newVersion;

    @Nullable
    public final String child;

    @Nullable
    public final String childName;

    public Transform(long logicalTimestamp, String root, String oid, ObjectType objectType, TransformType transformType, long newVersion, @Nullable String child, @Nullable String childName) {
        this.logicalTimestamp = logicalTimestamp;
        this.root = root;
        this.oid = oid;
        this.objectType = objectType;
        this.transformType = transformType;
        this.newVersion = newVersion;
        this.child = child;
        this.childName = childName;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Transform other = (Transform) o;
        return logicalTimestamp == other.logicalTimestamp
                && root.equals(other.root)
                && oid.equals(other.oid)
                && objectType.equals(other.objectType)
                && transformType == other.transformType
                && newVersion == other.newVersion
                && Objects.equal(child, other.child)
                && Objects.equal(childName, other.childName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(logicalTimestamp, root, oid, objectType, newVersion, transformType, child, childName);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("logicalTimestamp", logicalTimestamp)
                .add("sid", root)
                .add("oid", oid)
                .add("objectType", objectType)
                .add("transformType", transformType)
                .add("newVersion", newVersion)
                .add("child", child)
                .add("childName", childName)
                .toString();
    }
}
