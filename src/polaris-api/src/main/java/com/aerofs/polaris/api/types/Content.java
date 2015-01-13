package com.aerofs.polaris.api.types;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@SuppressWarnings("unused")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
public final class Content {

    public static final String INVALID_HASH = null;
    public static final long INVALID_MODIFICATION_TIME = -1;
    public static final long INVALID_SIZE = -1;

    @NotNull
    @Size(min = 1)
    private String oid;

    @Min(0)
    private long version;

    @Nullable
    private String hash;

    @Min(0)
    private long size;

    @Min(0)
    private long mtime;

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

    private Content() { }

    public String getOid() {
        return oid;
    }

    private void setOid(String oid) {
        this.oid = oid;
    }

    public long getVersion() {
        return version;
    }

    private void setVersion(long version) {
        this.version = version;
    }

    @Nullable
    public String getHash() {
        return hash;
    }

    private void setHash(@Nullable String hash) {
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
