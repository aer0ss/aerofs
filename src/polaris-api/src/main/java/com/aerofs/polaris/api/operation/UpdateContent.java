package com.aerofs.polaris.api.operation;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public final class UpdateContent extends Operation {

    @Min(0)
    public long localVersion;

    @NotNull
    @Size(min = 1)
    public String hash;

    @Min(0)
    public long size;

    @Min(0)
    public long mtime;

    public UpdateContent() {
        super(OperationType.UPDATE_CONTENT);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UpdateContent other = (UpdateContent) o;
        return type == other.type && localVersion == other.localVersion && hash.equals(other.hash) && size == other.size && mtime == other.mtime;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, localVersion, hash, size, mtime);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("localVersion", localVersion)
                .add("hash", hash)
                .add("size", size)
                .add("mtime", mtime)
                .toString();
    }
}
