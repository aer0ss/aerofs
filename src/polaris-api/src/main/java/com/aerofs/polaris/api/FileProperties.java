package com.aerofs.polaris.api;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public final class FileProperties {

    @NotNull
    @Size(min = 1)
    public final String oid;

    @Min(0)
    public final long version;

    @Nullable
    public final String hash;

    @Min(0)
    public final long size;

    @Min(0)
    public final long mtime;

    public FileProperties(String oid, long version) {
        this(oid, version, Constants.INVALID_HASH, Constants.INVALID_SIZE, Constants.INVALID_MODIFICATION_TIME);
    }

    public FileProperties(String oid, long version, @Nullable String hash, long size, long mtime) {
        this.oid = oid;
        this.version = version;
        this.hash = hash;
        this.size = size;
        this.mtime = mtime;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileProperties other = (FileProperties) o;
        return oid.equals(other.oid)
                && version == other.version
                && Objects.equal(hash, other.hash)
                && size == other.size
                && mtime == other.mtime;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(oid, version, hash, size, mtime);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("oid", oid)
                .add("version", version)
                .add("hash", hash)
                .add("size", size)
                .add("mtime", mtime)
                .toString();
    }
}
