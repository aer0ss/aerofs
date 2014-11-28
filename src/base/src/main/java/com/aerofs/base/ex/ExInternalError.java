/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

/**
 * Only code that parses PBException can generate this exception. All exceptions that are not a
 * sub-type of AbstractExWirable will be translated into PBException.INTERNAL_ERROR and in turn
 * converted to ExInternalError by the receiver who parses the PBException message.
 */
public class ExInternalError extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    ExInternalError(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.INTERNAL_ERROR;
    }
}
