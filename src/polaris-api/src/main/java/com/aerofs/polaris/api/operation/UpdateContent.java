package com.aerofs.polaris.api.operation;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public final class UpdateContent extends Operation {

    @Min(0)
    public long localVersion;

    @NotNull
    public String contentHash;

    @Min(0)
    public long contentSize;

    @Min(0)
    public long contentMTime;

    public UpdateContent() {
        super(OperationType.UPDATE_CONTENT);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UpdateContent other = (UpdateContent) o;
        return type == other.type && localVersion == other.localVersion && contentHash.equals(other.contentHash) && contentSize == other.contentSize && contentMTime == other.contentMTime;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, localVersion, contentHash, contentSize, contentMTime);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("localVersion", localVersion)
                .add("contentHash", contentHash)
                .add("contentSize", contentSize)
                .add("contentMTime", contentMTime)
                .toString();
    }
}
