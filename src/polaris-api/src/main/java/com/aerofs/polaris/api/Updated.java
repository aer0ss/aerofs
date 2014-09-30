package com.aerofs.polaris.api;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public final class Updated {

    @Min(0)
    public final long transformTimestamp;

    @NotNull
    public final LogicalObject object;

    public Updated(long transformTimestamp, LogicalObject object) {
        this.transformTimestamp = transformTimestamp;
        this.object = object;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Updated other = (Updated) o;
        return transformTimestamp == other.transformTimestamp && object.equals(other.object);
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
