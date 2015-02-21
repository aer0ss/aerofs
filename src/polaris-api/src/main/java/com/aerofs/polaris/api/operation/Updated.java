package com.aerofs.polaris.api.operation;

import com.aerofs.polaris.api.types.LogicalObject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public final class Updated {

    @Min(0)
    public final long transformTimestamp;

    @NotNull
    @Valid
    public final LogicalObject object;

    @JsonCreator
    public Updated(
            @JsonProperty("transform_timestamp") long transformTimestamp,
            @JsonProperty("object") LogicalObject object) {
        this.transformTimestamp = transformTimestamp;
        this.object = object;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Updated other = (Updated) o;
        return transformTimestamp == other.transformTimestamp && Objects.equal(object, other.object);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(object, transformTimestamp);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("transformTimestamp", transformTimestamp)
                .add("object", object)
                .toString();
    }
}
