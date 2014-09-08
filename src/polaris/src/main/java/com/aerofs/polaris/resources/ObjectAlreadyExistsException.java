package com.aerofs.polaris.resources;

import com.aerofs.polaris.PolarisException;

public final class ObjectAlreadyExistsException extends PolarisException {

    private static final long serialVersionUID = -2311189035465461131L;

    public ObjectAlreadyExistsException(String root, String oid) {
        super(oid + " already exists in " + root);
    }
}
