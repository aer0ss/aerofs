package com.aerofs.polaris.api;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public final class Update {

    @NotNull
    @Min(0)
    public long localVersion;

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

        return localVersion == other.localVersion
                && transformType == other.transformType
                && Objects.equal(child, other.child)
                && Objects.equal(childObjectType, other.childObjectType)
                && Objects.equal(childName, other.childName)
                && Objects.equal(contentHash, other.contentHash)
                && contentMtime == other.contentMtime
                && contentSize == other.contentSize;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(localVersion, child, childName, contentHash, contentMtime, contentSize, transformType, childObjectType);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("localVersion", localVersion)
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
