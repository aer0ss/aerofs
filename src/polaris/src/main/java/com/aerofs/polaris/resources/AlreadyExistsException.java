package com.aerofs.polaris.resources;

import com.aerofs.polaris.PolarisException;

public final class AlreadyExistsException extends PolarisException {

    private static final long serialVersionUID = -2311189035465461131L;

    public AlreadyExistsException(String root, String oid) {
        super(oid + " already exists in " + root);
    }
}
