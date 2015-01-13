package com.aerofs.polaris.api.operation;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@SuppressWarnings("unused")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
public final class OperationResult {

    @NotNull
    @Size(min = 1)
    @Valid
    private List<Updated> updated;

    public OperationResult(List<Updated> updated) {
        this.updated = updated;
    }

    public OperationResult() { }

    public List<Updated> getUpdated() {
        return updated;
    }

    private void setUpdated(List<Updated> updated) {
        this.updated = updated;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OperationResult other = (OperationResult) o;
        return Objects.equal(updated, other.updated);
    }

    @Override
    public int hashCode() {
        return updated.hashCode();
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("updated", updated)
                .toString();
    }
}
