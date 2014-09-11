package com.aerofs.polaris.api;

public enum ErrorCodes {

    VERSION_CONFLICT         (111),
    NAME_CONFLICT            (112),
    INVALID_OPERATION_ON_TYPE(113),
    NO_SUCH_OBJECT           (114);

    private final int code;

    ErrorCodes(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
