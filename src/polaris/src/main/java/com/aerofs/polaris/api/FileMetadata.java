package com.aerofs.polaris.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Objects;

import javax.annotation.Nullable;

public final class FileMetadata {

    //
    // constants
    //

    @JsonIgnore
    public static final String INVALID_HASH = null;

    @JsonIgnore
    public static final long INVALID_MODIFICATION_TIME = -1;

    @JsonIgnore
    public static final long INVALID_SIZE = -1;

    //
    // fields
    //

    public final String oid;

    public final long version;

    @Nullable
    public final String hash;

    public final long modificationTime;

    public final long size;

    public FileMetadata(String oid, long version) {
        this(oid, version, INVALID_HASH, INVALID_MODIFICATION_TIME, INVALID_SIZE);
    }

    public FileMetadata(String oid, long version, @Nullable String hash, long modificationTime, long size) {
        this.oid = oid;
        this.version = version;
        this.hash = hash;
        this.modificationTime = modificationTime;
        this.size = size;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileMetadata other = (FileMetadata) o;
        return oid.equals(other.oid)
                && version == other.version
                && Objects.equal(hash, other.hash)
                && modificationTime == other.modificationTime
                && size == other.size;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(oid, version, hash, modificationTime, size);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("oid", oid)
                .add("version", version)
                .add("hash", hash)
                .add("modificationTime", modificationTime)
                .add("size", size)
                .toString();
    }
}
