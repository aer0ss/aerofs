package com.aerofs.polaris.api;

import com.google.common.base.Objects;

import javax.annotation.Nullable;

public final class Transform {

    public final long changeId;

    public final String root;

    public final String oid;

    public final TransformType transformType;

    public final long version;

    @Nullable
    public final String child;

    @Nullable
    public final String childName;

    public Transform(long changeId, String root, String oid, TransformType transformType, long version, @Nullable String child, @Nullable String childName) {
        this.changeId = changeId;
        this.root = root;
        this.oid = oid;
        this.transformType = transformType;
        this.version = version;
        this.child = child;
        this.childName = childName;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Transform other = (Transform) o;
        return changeId == other.changeId
                && root.equals(other.root)
                && oid.equals(other.oid)
                && transformType == other.transformType
                && version == other.version
                && (child == null ? other.child == null : child.equals(other.child))
                && (childName == null ? other.childName == null : childName.equals(other.childName));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(changeId, root, oid, version, transformType, child, childName);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("changeId", changeId)
                .add("sid", root)
                .add("oid", oid)
                .add("transformType", transformType)
                .add("version", version)
                .add("child", child)
                .add("childName", childName)
                .toString();
    }
}