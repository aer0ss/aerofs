package com.aerofs.polaris.api;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public final class Update {

    @NotNull
    @Min(0)
    public long expectedVersion;

    @NotNull
    public TransformType transformType;

    @Nullable
    public String child;

    @Nullable
    public ObjectType childObjectType;

    @Nullable
    public String childName;

    @Nullable
    public String contentHash;

    @Min(0)
    public long contentMtime;

    @Min(0)
    public long contentSize;

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Update other = (Update) o;

        return expectedVersion == other.expectedVersion
                && transformType == other.transformType
                && (child == null ? other.child == null : child.equals(other.child))
                && (childObjectType == null ? other.childObjectType == null : childObjectType == other.childObjectType)
                && (childName == null ? other.childName == null : childName.equals(other.childName))
                && (contentHash == null ? other.contentHash == null : contentHash.equals(other.contentHash))
                && contentMtime == other.contentMtime
                && contentSize == other.contentSize;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(expectedVersion, child, childName, contentHash, contentMtime, contentSize, transformType, childObjectType);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("expectedVersion", expectedVersion)
                .add("transformType", transformType)
                .add("child", child)
                .add("childObjectType", childObjectType)
                .add("childName", childName)
                .add("contentHash", contentHash)
                .add("contentMtime", contentMtime)
                .add("contentSize", contentSize)
                .toString();
    }
}
