package com.aerofs.polaris.api.operation;

import com.aerofs.ids.UniqueID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import java.util.List;

public final class Share extends Operation {
    // null for previous implementation of share operation
    public final @Nullable UniqueID child;

    @JsonCreator
    public Share(@JsonProperty("child") @Nullable UniqueID child) {
        super(OperationType.SHARE);
        this.child = child;
    }

    @Override
    public List<UniqueID> affectedOIDs() {
        if (this.child == null) {
            return super.affectedOIDs();
        } else {
            return Lists.newArrayList(this.child);
        }
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Share other = (Share) o;
        return type == other.type && Objects.equal(child, other.child);
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
