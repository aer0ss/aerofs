package com.aerofs.polaris.api.batch.transform;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@SuppressWarnings("unused")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
public final class TransformBatchResult {

    @NotNull
    @Size(min = 1)
    @Valid
    private List<TransformBatchOperationResult> results;


    public TransformBatchResult(int resultCount) {
        this.results = Lists.newArrayListWithCapacity(resultCount);
    }

    private TransformBatchResult() { }

    public List<TransformBatchOperationResult> getResults() {
        return results;
    }

    private void setResults(List<TransformBatchOperationResult> results) {
        this.results = results;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransformBatchResult other = (TransformBatchResult) o;
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
