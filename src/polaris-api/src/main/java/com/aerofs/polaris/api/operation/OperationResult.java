package com.aerofs.polaris.api.operation;

import com.aerofs.ids.UniqueID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

public final class OperationResult {

    @NotNull
    @Valid
    public final List<Updated> updated;

    @Nullable
    public final UniqueID jobID;

    public OperationResult(List<Updated> updated)
    {
        this(updated, null);
    }

    @JsonCreator
    public OperationResult(@JsonProperty("updated") List<Updated> updated,
                           @JsonProperty("job_id") @Nullable UniqueID jobID)
    {
        this.updated = updated;
        this.jobID = jobID;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OperationResult other = (OperationResult) o;
        return Objects.equal(updated, other.updated) && Objects.equal(this.jobID, other.jobID);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(updated, jobID);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("updated", updated)
                .add("job_id", jobID)
                .toString();
    }
}
