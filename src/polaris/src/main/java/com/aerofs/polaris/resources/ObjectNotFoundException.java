package com.aerofs.polaris.resources;

import com.aerofs.polaris.PolarisException;

public final class ObjectNotFoundException extends PolarisException {

    private static final long serialVersionUID = 5816361021481101865L;

    public ObjectNotFoundException(String oid) {
        super("cannot find " + oid);
    }
}
