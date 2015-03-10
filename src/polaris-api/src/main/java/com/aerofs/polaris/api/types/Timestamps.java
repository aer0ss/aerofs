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
    public final UniqueID store;

    @Min(0)
    public final long databaseTimestamp;

    @Min(-1)
    public final long notifiedTimestamp;

    @JsonCreator
    public Timestamps(
            @JsonProperty("store") UniqueID store,
            @JsonProperty("database_timestamp") long databaseTimestamp,
            @JsonProperty("notified_timestamp") long notifiedTimestamp) {
        this.store = store;
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
                && Objects.equal(store, other.store);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(store, databaseTimestamp, notifiedTimestamp);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("store", store)
                .add("databaseTimestamp", databaseTimestamp)
                .add("notifiedTimestamp", notifiedTimestamp)
                .toString();
    }
}
