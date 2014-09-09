package com.aerofs.polaris.resources;

import com.aerofs.polaris.PolarisException;

public final class InvalidIDException extends PolarisException {

    private static final long serialVersionUID = 314629701369643076L;

    public InvalidIDException(String oid) {
        super("bad oid " + oid);
    }
}
