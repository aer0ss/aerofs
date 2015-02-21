package com.aerofs.polaris.api.operation;

import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.PolarisUtilities;
import com.aerofs.polaris.api.types.AeroTypes;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Arrays;

public final class MoveChild extends Operation {

    @NotNull
    public final UniqueID child;

    @NotNull
    public final UniqueID newParent;

    @JsonSerialize(using = AeroTypes.UTF8StringSerializer.class)
    @JsonDeserialize(using = AeroTypes.UTF8StringDeserializer.class)
    @NotNull
    @Size(min = 1)
    public final byte[] newChildName;

    public MoveChild(UniqueID child, UniqueID newParent, String newChildName) {
        this(child, newParent, PolarisUtilities.stringToUTF8Bytes(newChildName));
    }

    @JsonCreator
    public MoveChild(
            @JsonProperty("child") UniqueID child,
            @JsonProperty("new_parent") UniqueID newParent,
            @JsonProperty("new_child_name") byte[] newChildName) {
        super(OperationType.MOVE_CHILD);
        this.child = child;
        this.newParent = newParent;
        this.newChildName = newChildName;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MoveChild other = (MoveChild) o;
        return type == other.type
                && Objects.equal(child, other.child)
                && Objects.equal(newParent, other.newParent)
                && Arrays.equals(newChildName, other.newChildName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, child, newParent, newChildName);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("type", type)
                .add("child", child)
                .add("newParent", newParent)
                .add("newChildName", newChildName)
                .toString();
    }
}
