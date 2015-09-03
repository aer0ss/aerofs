package com.aerofs.polaris.api.operation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.base.Objects;

import javax.annotation.Nullable;

public class Restore extends Operation {
    @JsonCreator
    public Restore() {
        super(OperationType.RESTORE);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Restore other = (Restore) o;
        return type == other.type;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("type", type)
                .toString();
    }
}
