package com.aerofs.polaris.api.types;

import com.aerofs.ids.UniqueID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Arrays;

public final class Content {

    public static final byte[] INVALID_HASH = null;
    public static final long INVALID_MODIFICATION_TIME = -1;
    public static final long INVALID_SIZE = -1;

    @NotNull
    public final UniqueID oid;

    @Min(0)
    public final long version;

    @JsonSerialize(using = AeroTypes.Base16Serializer.class)
    @JsonDeserialize(using = AeroTypes.Base16Deserializer.class)
    public final @Nullable byte[] hash;

    @Min(0)
    public final long size;

    @Min(0)
    public final long mtime;

    public Content(UniqueID oid, long version) {
        this(oid, version, INVALID_HASH, INVALID_SIZE, INVALID_MODIFICATION_TIME);
    }

    @JsonCreator
    public Content(
            @JsonProperty("oid") UniqueID oid,
            @JsonProperty("version") long version,
            @JsonProperty("hash") @Nullable byte[] hash,
            @JsonProperty("size") long size,
            @JsonProperty("mtime") long mtime) {
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
                && Arrays.equals(hash, other.hash)
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
