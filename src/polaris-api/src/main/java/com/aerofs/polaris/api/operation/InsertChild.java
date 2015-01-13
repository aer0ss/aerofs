package com.aerofs.polaris.api.operation;

import com.aerofs.polaris.api.Filenames;
import com.aerofs.polaris.api.types.ObjectType;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Arrays;

@SuppressWarnings("unused")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
public final class InsertChild extends Operation {

    @NotNull
    @Size(min = 1)
    private String child;

    @Nullable
    private ObjectType childObjectType;

    @NotNull
    @Size(min = 1)
    private byte[] childName;

    public InsertChild(String child, @Nullable ObjectType childObjectType, String childName) {
        this(child, childObjectType, Filenames.toBytes(childName));
    }

    public InsertChild(String child, @Nullable ObjectType childObjectType, byte[] childName) {
        super(OperationType.INSERT_CHILD);
        this.child = child;
        this.childObjectType = childObjectType;
        this.childName = childName;
    }

    private InsertChild() { super(OperationType.INSERT_CHILD); }

    public String getChild() {
        return child;
    }

    private void setChild(String child) {
        this.child = child;
    }

    @Nullable
    public ObjectType getChildObjectType() {
        return childObjectType;
    }

    private void setChildObjectType(@Nullable ObjectType childObjectType) {
        this.childObjectType = childObjectType;
    }

    public String getChildName() {
        return new String(childName, Charsets.UTF_8);
    }

    @JsonIgnore
    public byte[] getChildNameBytes() {
        return childName;
    }

    private void setChildName(String childName) {
        this.childName = Filenames.toBytes(childName);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InsertChild other = (InsertChild) o;
        return type == other.type && Objects.equal(child, other.child) && Objects.equal(childObjectType, other.childObjectType) && Arrays.equals(childName, other.childName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, child, childObjectType, childName);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("type", type)
                .add("child", child)
                .add("childObjectType", childObjectType)
                .add("childName", childName)
                .toString();
    }
}
