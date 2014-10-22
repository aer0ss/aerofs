package com.aerofs.polaris.api.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public final class Content {

    @JsonIgnore
    public static final String INVALID_HASH = null;

    @JsonIgnore
    public static final long INVALID_MODIFICATION_TIME = -1;

    @JsonIgnore
    public static final long INVALID_SIZE = -1;

    @NotNull
    @Size(min = 1)
    public String oid;

    @Min(0)
    public long version;

    @Nullable
    public String hash;

    @Min(0)
    public long size;

    @Min(0)
    public long mtime;

    /**
     * For Jackson use only - do not use directly.
     */
    @SuppressWarnings("unused")
    private Content() { }

    public Content(String oid, long version) {
        this(oid, version, INVALID_HASH, INVALID_SIZE, INVALID_MODIFICATION_TIME);
    }

    public Content(String oid, long version, @Nullable String hash, long size, long mtime) {
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

        Content other = (Content) o;
        return Objects.equal(oid, other.oid)
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
