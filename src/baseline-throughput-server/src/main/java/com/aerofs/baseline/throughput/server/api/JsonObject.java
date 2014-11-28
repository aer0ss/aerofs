package com.aerofs.baseline.throughput.server.api;

import com.google.common.base.Objects;
import org.hibernate.validator.constraints.NotBlank;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;

public final class JsonObject {

    @NotBlank
    public String string;

    @Min(1)
    public long number;

    @SuppressWarnings("unused")
    private JsonObject() {
        // noop
    }

    public JsonObject(String string, long number) {
        this.string = string;
        this.number = number;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JsonObject other = (JsonObject) o;
        return Objects.equal(string, other.string) && number == other.number;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(string, number);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("string", string)
                .add("number", number)
                .toString();
    }
}
