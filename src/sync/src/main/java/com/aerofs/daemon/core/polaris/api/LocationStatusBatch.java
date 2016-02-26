package com.aerofs.daemon.core.polaris.api;

import com.google.common.base.Objects;

import java.util.Collection;

public final class LocationStatusBatch
{

    public final Collection<LocationStatusBatchOperation> operations;

    public LocationStatusBatch(Collection<LocationStatusBatchOperation> operations) {
        this.operations = operations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocationStatusBatch other = (LocationStatusBatch) o;
        return Objects.equal(operations, other.operations);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(operations);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("operations", operations).toString();
    }
}
