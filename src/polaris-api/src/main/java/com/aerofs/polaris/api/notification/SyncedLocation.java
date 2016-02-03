package com.aerofs.polaris.api.notification;

import com.aerofs.ids.OID;
import com.aerofs.ids.UniqueID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.primitives.Longs;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import java.util.Arrays;
import java.util.Collection;

public final class SyncedLocation {

    public static int SIZE = UniqueID.LENGTH + Long.BYTES;

    @NotNull
    public final OID oid;

    @Min(0)
    public final long version;

    public static byte[] getCollectionBytes(Collection<SyncedLocation> syncedLocations) {
        byte[] bytes = new byte[syncedLocations.size() * SIZE];
        int index = 0;
        for (SyncedLocation syncedLocation : syncedLocations) {
            System.arraycopy(syncedLocation.getBytes(), 0, bytes, index, SIZE);
            index += SIZE;
        }
        return bytes;
    }

    @JsonCreator
    public SyncedLocation(@JsonProperty("oid") OID oid, @JsonProperty("version") long version) {
        this.oid = oid;
        this.version = version;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SyncedLocation update = (SyncedLocation) o;
        return Objects.equal(oid, update.oid) && Objects.equal(version, update.version);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(oid, version);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("oid", oid)
                .add("version", version)
                .toString();
    }

    //TODO: varint encode the version
    public byte[] getBytes() {
        byte[] bytes = Arrays.copyOf(this.oid.getBytes(), SIZE);
        System.arraycopy(Longs.toByteArray(this.version), 0, bytes, UniqueID.LENGTH, Long.BYTES);
        return bytes;
    }
}
