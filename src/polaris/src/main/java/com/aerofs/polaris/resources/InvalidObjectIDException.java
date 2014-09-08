package com.aerofs.polaris.resources;

import com.aerofs.polaris.PolarisException;

public final class InvalidObjectIDException extends PolarisException {

    private static final long serialVersionUID = 314629701369643076L;

    public InvalidObjectIDException(String oid) {
        super("bad oid " + oid);
    }
}
