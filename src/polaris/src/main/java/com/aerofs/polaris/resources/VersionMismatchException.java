package com.aerofs.polaris.resources;

import com.aerofs.polaris.PolarisException;

public final class VersionMismatchException extends PolarisException {

    private static final long serialVersionUID = -7931192284107460967L;

    public VersionMismatchException(String oid, long expectedVersion, long actualVersion) {
        super("expected version " + expectedVersion + " but actual version is " + actualVersion + " for " + oid);
    }
}
