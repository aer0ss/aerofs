package com.aerofs.polaris;

/**
 * Base class for all polaris exceptions.
 * All exceptions thrown by resources should derive from this class
 * to ensure that a useful HTTP response status code is
 * generated and returned by {@link com.aerofs.polaris.PolarisExceptionMapper}.
 */
public abstract class PolarisException extends Exception {

    private static final long serialVersionUID = -6203452032734130429L;

    public PolarisException(String message) {
        super(message);
    }
}
