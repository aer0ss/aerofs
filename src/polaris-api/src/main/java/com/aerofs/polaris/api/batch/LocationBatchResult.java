package com.aerofs.polaris.api.batch;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

public final class LocationBatchResult {

    @NotNull
    @Size(min = 1)
    @Valid
    public List<LocationBatchOperationResult> results;

    /**
     * For Jackson use only - do not use directly.
     */
    @SuppressWarnings("unused")
    private LocationBatchResult() { }

    public LocationBatchResult(int resultCount) {
        this.results = Lists.newArrayListWithCapacity(resultCount);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocationBatchResult other = (LocationBatchResult) o;
        return Objects.equal(results, other.results);
    }

    @Override
    public int hashCode() {
        return results.hashCode();
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("results", results)
                .toString();
    }
}
