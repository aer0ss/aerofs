package com.aerofs.polaris.api.batch.location;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@SuppressWarnings("unused")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
public final class LocationBatch {

    @NotNull
    @Size(min = 1)
    @Valid
    private List<LocationBatchOperation> operations;

    public LocationBatch(List<LocationBatchOperation> operations) {
        this.operations = operations;
    }

    private LocationBatch() {}

    public List<LocationBatchOperation> getOperations() {
        return operations;
    }

    private void setOperations(List<LocationBatchOperation> operations) {
        this.operations = operations;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocationBatch other = (LocationBatch) o;
        return Objects.equal(operations, other.operations);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(operations);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("operations", operations)
                .toString();
    }
}
