package com.aerofs.polaris.api.operation;

import com.aerofs.polaris.api.Transform;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;

public final class AppliedTransforms {

    @Min(0)
    public final long maxTransformCount;

    @NotNull
    public final List<Transform> transforms;

    public AppliedTransforms(long maxTransformCount, List<Transform> transforms) {
        this.maxTransformCount = maxTransformCount;
        this.transforms = transforms;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AppliedTransforms other = (AppliedTransforms) o;
        return maxTransformCount == other.maxTransformCount && transforms.equals(other.transforms);
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
