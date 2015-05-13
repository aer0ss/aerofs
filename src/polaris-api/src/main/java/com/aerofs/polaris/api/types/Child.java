package com.aerofs.polaris.api.types;

import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.PolarisUtilities;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Arrays;

public class Child {

    @NotNull
    public final UniqueID oid;

    @JsonSerialize(using = AeroTypes.UTF8StringSerializer.class)
    @JsonDeserialize(using = AeroTypes.UTF8StringDeserializer.class)
    @NotNull
    @Size(min = 1)
    public final byte[] name;

    @NotNull
    public final ObjectType objectType;

    public Child(UniqueID oid, ObjectType objectType, String name) {
        this(oid, objectType, PolarisUtilities.stringToUTF8Bytes(name));
    }

    @JsonCreator
    public Child(
            @JsonProperty("oid") UniqueID oid,
            @JsonProperty("object_type") ObjectType objectType,
            @JsonProperty("name") byte[] name) {
        this.oid = oid;
        this.name = name;
        this.objectType = objectType;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Child other = (Child) o;
        return Objects.equal(oid, other.oid)
                && Arrays.equals(name, other.name)
                && Objects.equal(objectType, other.objectType);
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
