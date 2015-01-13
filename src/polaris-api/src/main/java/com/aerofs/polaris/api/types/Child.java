package com.aerofs.polaris.api.types;

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
public final class Child {

    @NotNull
    @Size(min = 1)
    private String oid;

    @NotNull
    @Size(min = 1)
    private byte[] name;

    @NotNull
    private ObjectType objectType;

    public Child(String oid, ObjectType objectType, byte[] name) {
        this.oid = oid;
        this.name = name;
        this.objectType = objectType;
    }

    private Child() { }

    public String getOid() {
        return oid;
    }

    private void setOid(String oid) {
        this.oid = oid;
    }

    public String getName() {
        return Filenames.fromBytes(name);
    }

    @JsonIgnore
    public byte[] getNameBytes() {
        return name;
    }

    private void setName(String name) {
        this.name = Filenames.toBytes(name);
    }

    public ObjectType getObjectType() {
        return objectType;
    }

    private void setObjectType(ObjectType objectType) {
        this.objectType = objectType;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Child other = (Child) o;
        return Objects.equal(oid, other.oid) && Arrays.equals(name, other.name) && Objects.equal(objectType, other.objectType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(oid, name, objectType);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("oid", oid)
                .add("name", name)
                .add("objectType", objectType)
                .toString();
    }
}
