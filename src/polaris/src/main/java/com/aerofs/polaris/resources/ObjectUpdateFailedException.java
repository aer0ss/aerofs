package com.aerofs.polaris.resources;

import com.aerofs.polaris.PolarisException;

public final class ObjectUpdateFailedException extends PolarisException {

    private static final long serialVersionUID = 1623333046834562788L;

    public ObjectUpdateFailedException(String oid) {
        super("fail update for " + oid);
    }
}
