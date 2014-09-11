package com.aerofs.polaris.logical;

import com.aerofs.polaris.PolarisException;

public final class VersionConflictException extends PolarisException {

    private static final long serialVersionUID = -7931192284107460967L;

    public VersionConflictException(String oid, long expectedVersion, long actualVersion) {
        super("expected version " + expectedVersion + " but actual version is " + actualVersion + " for " + oid);
    }
}
