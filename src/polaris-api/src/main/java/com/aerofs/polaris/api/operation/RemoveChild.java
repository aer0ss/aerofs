package com.aerofs.polaris.api.operation;

import com.aerofs.ids.OID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public final class RemoveChild extends Operation {

    @NotNull
    public final OID child;

    @JsonCreator
    public RemoveChild(@JsonProperty("child") OID child) {
        super(OperationType.REMOVE_CHILD);
        this.child = child;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RemoveChild other = (RemoveChild) o;
        return type == other.type && Objects.equal(child, other.child);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, child);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("type", type)
                .add("child", child)
                .toString();
    }
}
