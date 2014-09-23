package com.aerofs.polaris.api;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public final class BatchOperation {

    @NotNull
    public String oid;

    @Valid
    @NotNull
    public Update update;

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BatchOperation other = (BatchOperation) o;
        return oid.equals(other.oid) && update.equals(other.update);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(oid, update);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("oid", oid)
                .add("update", update)
                .toString();
    }
}
