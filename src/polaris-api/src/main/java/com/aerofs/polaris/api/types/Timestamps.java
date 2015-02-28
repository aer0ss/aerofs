package com.aerofs.polaris.api.types;

import com.aerofs.ids.UniqueID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public final class Timestamps {

    @NotNull
    public final UniqueID root;

    @Min(0)
    public final long databaseTimestamp;

    @Min(-1)
    public final long notifiedTimestamp;

    @JsonCreator
    public Timestamps(
            @JsonProperty("root") UniqueID root,
            @JsonProperty("database_timestamp") long databaseTimestamp,
            @JsonProperty("notified_timestamp") long notifiedTimestamp) {
        this.root = root;
        this.databaseTimestamp = databaseTimestamp;
        this.notifiedTimestamp = notifiedTimestamp;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Timestamps other = (Timestamps) o;
        return Objects.equal(databaseTimestamp, other.databaseTimestamp)
                && Objects.equal(notifiedTimestamp, other.notifiedTimestamp)
                && Objects.equal(root, other.root);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(root, databaseTimestamp, notifiedTimestamp);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("root", root)
                .add("databaseTimestamp", databaseTimestamp)
                .add("notifiedTimestamp", notifiedTimestamp)
                .toString();
    }
}
