package com.aerofs.daemon.core.polaris.api;

public final class BatchError {

    public final PolarisError errorCode;

    public final String errorMessage;

    public BatchError(PolarisError errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
