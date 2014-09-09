package com.aerofs.polaris.resources;

import com.aerofs.polaris.PolarisException;

public final class UpdateFailedException extends PolarisException {

    private static final long serialVersionUID = 1623333046834562788L;

    public UpdateFailedException(String oid) {
        super("fail update for " + oid);
    }
}
