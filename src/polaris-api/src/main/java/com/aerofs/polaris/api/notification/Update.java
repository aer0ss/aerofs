package com.aerofs.polaris.api.notification;

import com.aerofs.ids.UniqueID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public final class Update {

    @NotNull
    public final UniqueID store;

    @Min(0)
    public final long latestLogicalTimestamp;

    @JsonCreator
    public Update(@JsonProperty("store") UniqueID store, @JsonProperty("latest_logical_timestamp") long latestLogicalTimestamp) {
        this.store = store;
        this.latestLogicalTimestamp = latestLogicalTimestamp;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Update update = (Update) o;
        return Objects.equal(latestLogicalTimestamp, update.latestLogicalTimestamp) && Objects.equal(store, update.store);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(store, latestLogicalTimestamp);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("store", store)
                .add("latestLogicalTimestamp", latestLogicalTimestamp)
                .toString();
    }
}
