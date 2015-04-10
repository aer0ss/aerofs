package com.aerofs.polaris.api.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;

/**
 * <strong>IMPORTANT:</strong> The type ids start at 1 because the Java ResultSet API
 * is "suboptimal" and returns 0 if the value in the column is null. Yeah. Really.
 */
public enum JobStatus {
    COMPLETED(1),

    RUNNING(2),

    FAILED(3),;

    public final int typeId;

    private JobStatus(int typeId) {
        this.typeId = typeId;
    }

    public static JobStatus fromTypeId(int typeId) throws IllegalArgumentException
    {
        for (JobStatus type : JobStatus.values()) {
            if (typeId == type.typeId) {
                return type;
            }
        }
        throw new IllegalArgumentException(typeId + " is not a valid job status id");
    }

    // Jackson has issues with having enums as objects, so we wrap up the status in a POJO
    public Response asResponse() {
        return new Response(this);
    }

    public static class Response {
        public JobStatus status;

        @JsonCreator
        public Response(@JsonProperty("status") JobStatus status) {
            this.status = status;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            JobStatus.Response other = (JobStatus.Response) o;
            return Objects.equal(status, other.status);
        }

        @Override
        public int hashCode() {
            return status.hashCode();
        }
    }
}

