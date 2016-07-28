/*
 * Copyright 2015 Air Computing Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.baseline.admin;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Result of a {@link HealthCheck} execution.
 */
@SuppressWarnings("unused")
@Immutable
public final class Status {

    /**
     * Result of a {@link HealthCheck} execution.
     */
    public enum Value {
        /** The health check executed successfully. */
        SUCCESS,

        /** The health check failed. */
        FAILURE,
    }

    private static final Status SUCCESS = new Status(Value.SUCCESS);
    private static final Status FAILURE = new Status(Value.FAILURE);

    /**
     * Factory method to return an instance of {@link Status}
     * with {@code status} of {@code SUCCESS} and no {@code message}.
     *
     * @return instance with {@code value} of {@code SUCCESS} and no {@code message}
     */
    public static Status success() {
        return SUCCESS;
    }

    /**
     * Factory method to return an instance of {@link Status}
     * with {@code status} of {@code FAILURE} and no {@code message}.
     *
     * @return {@code Status} instance with {@code value} of
     * {@code FAILURE} and no {@code message}
     */
    public static Status failure() {
        return FAILURE;
    }

    /**
     * Factory method to return an instance of {@link Status}
     * with {@code value} of {@code FAILURE} and the user-specified
     * {@code message}.
     *
     * @param message explanatory message describing the status failure
     *
     * @return {@code Status} instance with {@code value} of {@code FAILURE}
     * and the user-specified {@code message}
     */
    public static Status failure(String message) {
        Preconditions.checkNotNull(message, "cannot use null failure message");
        return new Status(Value.FAILURE, message);
    }

    //
    // actual status class
    // you can only create instances via the statics above
    //

    private final Value value;
    private final @Nullable String message;

    private Status(Value value) {
        this(value, null);
    }

    // NOTE: we can have only *one* constructor annotated with JsonCreator, so we choose the most general one
    // http://stackoverflow.com/questions/15931082/how-to-deserialize-a-class-with-overloaded-constructors-using-jsoncreator
    @JsonCreator
    private Status(@JsonProperty("status") Value value, @JsonProperty("message") @Nullable String message) {
        this.value = value;
        this.message = message;
    }

    /**
     * @return the health check {@code Status} indicating whether the health check succeeded or failed
     */
    public Value getValue() {
        return value;
    }

    /**
     * @return optional message explaining {@link #getValue}
     */
    public final @Nullable String getMessage() {
        return message;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

       Status other = (Status) o;
        return Objects.equal(value, other.value) && Objects.equal(message, other.message);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value, message);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("status", value)
                .add("message", message)
                .toString();
    }
}
