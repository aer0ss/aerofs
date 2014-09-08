package com.aerofs.polaris.api;

import com.aerofs.polaris.PolarisException;

public final class InvalidTypeException extends PolarisException {

    private static final long serialVersionUID = -440780278980512197L;

    public InvalidTypeException(int typeId) {
        super("unknown type with id " + typeId);
    }
}
