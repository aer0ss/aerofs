/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

/**
 * An exception that indicates that an operation failed because the current license
 * does not allow it.
 */
public class ExLicenseLimit extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExLicenseLimit(String msg)
    {
        super(msg);
    }

    @Override
    public Type getWireType()
    {
        return Type.LICENSE_LIMIT;
    }
}
