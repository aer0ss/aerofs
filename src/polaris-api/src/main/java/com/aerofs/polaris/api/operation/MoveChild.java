package com.aerofs.polaris.api.operation;

import com.aerofs.polaris.api.Filenames;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Arrays;

@SuppressWarnings("unused")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
public final class MoveChild extends Operation {

    @NotNull
    @Size(min = 1)
    private String child;

    @NotNull
    @Size(min = 1)
    private String newParent;

    @NotNull
    @Size(min = 1)
    private byte[] newChildName;

    public MoveChild(String child, String newParent, String newChildName) {
        this(child, newParent, Filenames.toBytes(newChildName));
    }

    public MoveChild(String child, String newParent, byte[] newChildName) {
        super(OperationType.MOVE_CHILD);
        this.child = child;
        this.newParent = newParent;
        this.newChildName = newChildName;
    }

    private MoveChild() { super(OperationType.MOVE_CHILD); }

    public String getChild() {
        return child;
    }

    private void setChild(String child) {
        this.child = child;
    }

    public String getNewParent() {
        return newParent;
    }

    private void setNewParent(String newParent) {
        this.newParent = newParent;
    }

    public String getNewChildName() {
        return Filenames.fromBytes(newChildName);
    }

    @JsonIgnore
    public byte[] getNewChildNameBytes() {
        return newChildName;
    }

    private void setNewChildName(String newChildName) {
        this.newChildName = Filenames.toBytes(newChildName);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MoveChild other = (MoveChild) o;
        return type == other.type && Objects.equal(child, other.child) && Objects.equal(newParent, other.newParent) && Arrays.equals(newChildName, other.newChildName);
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
