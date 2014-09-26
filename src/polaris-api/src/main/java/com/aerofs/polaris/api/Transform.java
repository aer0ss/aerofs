package com.aerofs.polaris.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Objects;

import javax.annotation.Nullable;

public final class Transform {

    public final long logicalTimestamp;

    @JsonIgnore
    public final String root;

    public final String oid;

    public final TransformType transformType;

    public final long newVersion;

    @Nullable
    public final String child;

    @Nullable
    public final ObjectType childObjectType;

    @Nullable
    public final String childName;

    public Transform(
            long logicalTimestamp,
            String root,
            String oid,
            TransformType transformType,
            long newVersion,
            @Nullable String child,
            @Nullable ObjectType childObjectType,
            @Nullable String childName) {
        this.logicalTimestamp = logicalTimestamp;
        this.root = root;
        this.oid = oid;
        this.transformType = transformType;
        this.newVersion = newVersion;
        this.child = child;
        this.childObjectType = childObjectType;
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
                && transformType == other.transformType
                && newVersion == other.newVersion
                && Objects.equal(child, other.child)
                && Objects.equal(childObjectType, other.childObjectType)
                && Objects.equal(childName, other.childName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(logicalTimestamp, root, oid, transformType, newVersion, child, childObjectType, childName);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("logicalTimestamp", logicalTimestamp)
                .add("sid", root)
                .add("oid", oid)
                .add("updateType", transformType)
                .add("newVersion", newVersion)
                .add("child", child)
                .add("childObjectType", childObjectType)
                .add("childName", childName)
                .toString();
    }
}
