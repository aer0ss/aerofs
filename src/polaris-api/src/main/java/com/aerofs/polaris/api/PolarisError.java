package com.aerofs.polaris.api;

public enum PolarisError {

    VERSION_CONFLICT(800),

    NAME_CONFLICT(801),

    INVALID_OPERATION_ON_TYPE(802),

    NO_SUCH_OBJECT(803),

    INSUFFICIENT_PERMISSIONS(804),

    PARENT_CONFLICT(805),

    OBJECT_LOCKED(806),

    CONDITION_FAILED(807),

    UNKNOWN(888);

    private final int code;

    PolarisError(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
