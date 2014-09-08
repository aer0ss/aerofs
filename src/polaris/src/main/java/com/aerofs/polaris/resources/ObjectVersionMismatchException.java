package com.aerofs.polaris.resources;

import com.aerofs.polaris.PolarisException;

public final class ObjectVersionMismatchException extends PolarisException {

    private static final long serialVersionUID = -7931192284107460967L;

    public ObjectVersionMismatchException(String oid, long expectedVersion, long actualVersion) {
        super("expected version " + expectedVersion + " but actual version is " + actualVersion + " for " + oid);
    }
}
