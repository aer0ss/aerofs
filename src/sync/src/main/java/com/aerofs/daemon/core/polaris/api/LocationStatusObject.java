package com.aerofs.daemon.core.polaris.api;

import com.google.common.base.Objects;

public class LocationStatusObject {
    public final String oid;
    public final long version;

    public LocationStatusObject(String oid, long version) {
        this.oid = oid;
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocationStatusObject other = (LocationStatusObject) o;
        return Objects.equal(oid, other.oid) && version == other.version;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(oid, version);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("oid", oid).add("version", version).toString();
    }
}
