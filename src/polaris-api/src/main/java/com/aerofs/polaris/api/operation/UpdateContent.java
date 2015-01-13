package com.aerofs.polaris.api.operation;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@SuppressWarnings("unused")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
public final class UpdateContent extends Operation {

    @Min(0)
    private long localVersion;

    @NotNull
    @Size(min = 1)
    private String hash;

    @Min(0)
    private long size;

    @Min(0)
    private long mtime;

    public UpdateContent(long localVersion, String hash, long size, long mtime) {
        super(OperationType.UPDATE_CONTENT);
        this.localVersion = localVersion;
        this.hash = hash;
        this.size = size;
        this.mtime = mtime;
    }

    private UpdateContent() { super(OperationType.UPDATE_CONTENT); }

    public long getLocalVersion() {
        return localVersion;
    }

    private void setLocalVersion(long localVersion) {
        this.localVersion = localVersion;
    }

    public String getHash() {
        return hash;
    }

    private void setHash(String hash) {
        this.hash = hash;
    }

    public long getSize() {
        return size;
    }

    private void setSize(long size) {
        this.size = size;
    }

    public long getMtime() {
        return mtime;
    }

    private void setMtime(long mtime) {
        this.mtime = mtime;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UpdateContent other = (UpdateContent) o;
        return type == other.type && localVersion == other.localVersion && Objects.equal(hash, other.hash) && size == other.size && mtime == other.mtime;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, localVersion, hash, size, mtime);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("type", type)
                .add("localVersion", localVersion)
                .add("hash", hash)
                .add("size", size)
                .add("mtime", mtime)
                .toString();
    }
}
