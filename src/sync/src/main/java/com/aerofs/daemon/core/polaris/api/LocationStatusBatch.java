package com.aerofs.daemon.core.polaris.api;

import com.google.common.base.Objects;

import java.util.Collection;

public final class LocationStatusBatch {
    public final Collection<LocationStatusObject> objects;

    public LocationStatusBatch(Collection<LocationStatusObject> operations) {
        this.objects = operations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocationStatusBatch other = (LocationStatusBatch) o;
        return Objects.equal(objects, other.objects);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(objects);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("operations", objects).toString();
    }
}
