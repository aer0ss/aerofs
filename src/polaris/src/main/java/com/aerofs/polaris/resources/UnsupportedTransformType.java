package com.aerofs.polaris.resources;

import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.TransformType;

public final class UnsupportedTransformType extends PolarisException {

    private static final long serialVersionUID = -4224943517034791271L;

    public UnsupportedTransformType(String oid, TransformType transformType) {
        super("cannot execute " + transformType + " on " + oid);
    }
}
