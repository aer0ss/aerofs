package com.aerofs.polaris.api.operation;

import com.aerofs.polaris.api.types.Transform;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;

public final class AppliedTransforms {

    @Min(0)
    public final long maxTransformCount;

    @NotNull
    @Valid
    public final List<Transform> transforms;

    @JsonCreator
    public AppliedTransforms(
            @JsonProperty("max_transform_count") long maxTransformCount,
            @JsonProperty("transforms") List<Transform> transforms) {
        this.maxTransformCount = maxTransformCount;
        this.transforms = transforms;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AppliedTransforms other = (AppliedTransforms) o;
        return maxTransformCount == other.maxTransformCount && Objects.equal(transforms, other.transforms);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(maxTransformCount, transforms);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("maxTransformCount", maxTransformCount)
                .add("transforms", transforms)
                .toString();
    }
}
