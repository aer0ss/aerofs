package com.aerofs.polaris.api.operation;

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

public final class RenameStore extends Operation {

    @JsonSerialize(using = AeroTypes.UTF8StringSerializer.class)
    @JsonDeserialize(using = AeroTypes.UTF8StringDeserializer.class)
    @NotNull
    @Size(min = 1)
    public final byte[] oldName;

    @JsonSerialize(using = AeroTypes.UTF8StringSerializer.class)
    @JsonDeserialize(using = AeroTypes.UTF8StringDeserializer.class)
    @NotNull
    @Size(min = 1)
    public final byte[] newName;

    public RenameStore(String oldName, String newName) {
        this(PolarisUtilities.stringToUTF8Bytes(oldName), PolarisUtilities.stringToUTF8Bytes(newName));
    }

    @JsonCreator
    public RenameStore(
            @JsonProperty("old_name") byte[] oldName,
            @JsonProperty("new_name") byte[] newName) {
        super(OperationType.RENAME_STORE);
        this.oldName = oldName;
        this.newName = newName;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RenameStore other = (RenameStore) o;
        return type == other.type
                && Objects.equal(oldName, other.oldName)
                && Objects.equal(newName, other.newName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, oldName, newName);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("type", type)
                .add("oldName", oldName)
                .add("newName", newName)
                .toString();
    }
}
