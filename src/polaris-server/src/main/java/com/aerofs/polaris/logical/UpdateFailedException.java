package com.aerofs.polaris.logical;

import com.aerofs.polaris.PolarisException;

public final class UpdateFailedException extends PolarisException {

    private static final long serialVersionUID = 1623333046834562788L;

    public UpdateFailedException(String oid, String message) {
        super("fail update for " + oid + ": " + message);
    }
}
