package com.aerofs.polaris.api.operation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

public final class OperationResult {

    @NotNull
    @Size(min = 1)
    @Valid
    public final List<Updated> updated;

    @JsonCreator
    public OperationResult(@JsonProperty("updated") List<Updated> updated) {
        this.updated = updated;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OperationResult other = (OperationResult) o;
        return Objects.equal(updated, other.updated);
    }

    @Override
    public int hashCode() {
        return updated.hashCode();
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("updated", updated)
                .toString();
    }
}
