package com.aerofs.polaris.api.operation;

import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.PolarisUtilities;
import com.aerofs.polaris.api.types.AeroTypes;
import com.aerofs.polaris.api.types.ObjectType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class InsertChild extends Operation {

    @NotNull
    public final UniqueID child;

    // Null for client-side optimizations of inserting a child that we already have records for
    @Nullable
    public final ObjectType childObjectType;

    @JsonSerialize(using = AeroTypes.UTF8StringSerializer.class)
    @JsonDeserialize(using = AeroTypes.UTF8StringDeserializer.class)
    @NotNull
    @Size(min = 1)
    public final byte[] childName;

    // only set from client-side for insert child operations resulting from a cross-store move, references the OID this object was originally
    @Nullable
    public final UniqueID migrant;

    // used for conversion only, set client-side
    @Nullable
    public final Map<DID, Long> versions;

    // used for conversion only, set client-side
    @Nullable
    public final List<UniqueID> aliases;

    public InsertChild(
            UniqueID child,
            @Nullable ObjectType childObjectType,
            String childName,
            @Nullable UniqueID migrant)
    {
        this(child, childObjectType, PolarisUtilities.stringToUTF8Bytes(childName), migrant, null, null);
    }

    @JsonCreator
    public InsertChild(
            @JsonProperty("child") UniqueID child,
            @JsonProperty("child_object_type") @Nullable ObjectType childObjectType,
            @JsonProperty("child_name") byte[] childName,
            @JsonProperty("migrant") @Nullable UniqueID migrant,
            @JsonProperty("version") @Nullable Map<DID, Long> versions,
            @JsonProperty("aliases") @Nullable List<UniqueID> aliases)
    {
        super(OperationType.INSERT_CHILD);
        this.child = child;
        this.childObjectType = childObjectType;
        this.childName = childName;
        this.migrant = migrant;
        this.versions = versions;
        this.aliases = aliases;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InsertChild other = (InsertChild) o;
        return type == other.type
                && Objects.equal(child, other.child)
                && Objects.equal(childObjectType, other.childObjectType)
                && Arrays.equals(childName, other.childName)
                && Objects.equal(migrant, other.migrant)
                && Objects.equal(versions, other.versions)
                && Objects.equal(aliases, other.aliases);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, child, childObjectType, childName, migrant, versions, aliases);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("type", type)
                .add("child", child)
                .add("childObjectType", childObjectType)
                .add("childName", childName)
                .add("migrant", migrant)
                .add("versions", versions)
                .add("aliases", aliases)
                .toString();
    }
}
