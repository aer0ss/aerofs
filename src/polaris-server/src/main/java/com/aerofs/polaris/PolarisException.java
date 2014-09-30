package com.aerofs.polaris;

import com.aerofs.polaris.api.PolarisError;
import com.google.common.base.Objects;

import java.util.Map;

/**
 * Base class for all polaris exceptions.
 * All exceptions thrown by resources should derive from this class
 * to ensure that a useful HTTP response status code is
 * generated and returned by {@link com.aerofs.polaris.PolarisExceptionMapper}.
 */
public abstract class PolarisException extends Exception {

    private final PolarisError errorCode;

    public PolarisException(PolarisError errorCode) {
        this.errorCode = errorCode;
    }

    public PolarisError getErrorCode() {
        return errorCode;
    }

    protected abstract String getSimpleMessage();

    protected abstract void addErrorFields(Map<String, Object> errorFields);

    @Override
    public String getLocalizedMessage() {
        return getMessage();
    }

    @Override
    public String getMessage() {
        return toString();
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("errorCode", errorCode)
                .add("errorMessage", getSimpleMessage())
                .toString();
    }
}
