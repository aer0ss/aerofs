package com.aerofs.polaris.api.operation;

import com.aerofs.ids.DID;
import com.aerofs.polaris.api.types.AeroTypes;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Arrays;
import java.util.Map;

public final class UpdateContent extends Operation {

    @Min(0)
    public final long localVersion;

    @JsonSerialize(using = AeroTypes.Base16Serializer.class)
    @JsonDeserialize(using = AeroTypes.Base16Deserializer.class)
    @NotNull
    @Size(min = 1)
    public final byte[] hash;

    @Min(0)
    public final long size;

    @Min(0)
    public final long mtime;

    // used for conversion only, set client-side
    @Nullable
    public final Map<DID, Long> versions;

    @JsonCreator
    public UpdateContent(
            @JsonProperty("local_version") long localVersion,
            @JsonProperty("hash") byte[] hash,
            @JsonProperty("size") long size,
            @JsonProperty("mtime") long mtime,
            @JsonProperty("version") @Nullable Map<DID, Long> versions) {
        super(OperationType.UPDATE_CONTENT);
        this.localVersion = localVersion;
        this.hash = hash;
        this.size = size;
        this.mtime = mtime;
        this.versions = versions;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UpdateContent other = (UpdateContent) o;
        return type == other.type
                && localVersion == other.localVersion
                && Arrays.equals(hash, other.hash)
                && size == other.size
                && mtime == other.mtime;
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
