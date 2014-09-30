package com.aerofs.polaris.api.batch;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

public final class BatchResult {

    @NotNull
    @Size(min = 1)
    public final List<BatchOperationResult> results;

    public BatchResult(int resultCount) {
        this.results = Lists.newArrayListWithCapacity(resultCount);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BatchResult other = (BatchResult) o;
        return results.equals(other.results);
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
