package com.aerofs.polaris.logical;

import com.aerofs.polaris.PolarisException;

public final class NameConflictException extends PolarisException {

    private static final long serialVersionUID = -397290595244198563L;

    public NameConflictException(String oid, String childName) {
        super("object named " + childName + " already exists in " + oid);
    }
}
