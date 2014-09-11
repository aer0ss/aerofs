package com.aerofs.polaris.logical;

import com.aerofs.polaris.PolarisException;

public final class NotFoundException extends PolarisException {

    private static final long serialVersionUID = 5816361021481101865L;

    public NotFoundException(String oid) {
        super("cannot find " + oid);
    }
}
