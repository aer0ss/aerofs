package com.aerofs.polaris.api;

import com.google.common.base.Objects;

import javax.annotation.Nullable;

public final class Transform {

    public final long changeId;

    public final String root;

    public final String oid;

    public final TransformType transformType;

    public final long newVersion;

    @Nullable
    public final String child;

    @Nullable
    public final String childName;

    public Transform(long changeId, String root, String oid, TransformType transformType, long newVersion, @Nullable String child, @Nullable String childName) {
        this.changeId = changeId;
        this.root = root;
        this.oid = oid;
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
        return changeId == other.changeId
                && root.equals(other.root)
                && oid.equals(other.oid)
                && transformType == other.transformType
                && newVersion == other.newVersion
                && Objects.equal(child, other.child)
                && Objects.equal(childName, other.childName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(changeId, root, oid, newVersion, transformType, child, childName);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("changeId", changeId)
                .add("sid", root)
                .add("oid", oid)
                .add("transformType", transformType)
                .add("newVersion", newVersion)
                .add("child", child)
                .add("childName", childName)
                .toString();
    }
}
