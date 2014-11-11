package com.aerofs.polaris.dao;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import java.util.UUID;

public final class Atomic {

    public final String id = UUID.randomUUID().toString().replace("-", "");

    public final int total;

    private int index;

    public Atomic(int total) {
        this.total = total;
    }

    public int incrementAndGet() {
        return ++index;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Atomic other = (Atomic) o;
        return Objects.equal(id, other.id) && total == other.total && index == other.index;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, total, index);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("id", id)
                .add("total", total)
                .add("index", index)
                .toString();
    }
}
