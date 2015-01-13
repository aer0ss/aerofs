package com.aerofs.polaris.api.operation;

import com.aerofs.polaris.api.types.LogicalObject;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@SuppressWarnings("unused")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
public final class Updated {

    @Min(0)
    private long transformTimestamp;

    @NotNull
    @Valid
    private LogicalObject object;

    public Updated(long transformTimestamp, LogicalObject object) {
        this.transformTimestamp = transformTimestamp;
        this.object = object;
    }

    private Updated() { }

    public long getTransformTimestamp() {
        return transformTimestamp;
    }

    private void setTransformTimestamp(long transformTimestamp) {
        this.transformTimestamp = transformTimestamp;
    }

    public LogicalObject getObject() {
        return object;
    }

    private void setObject(LogicalObject object) {
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
