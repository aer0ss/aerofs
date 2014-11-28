/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

/**
 * Thrown to indicate a failure returned from an external authentication service.
 * Examples include web-authentication timeout, or use of a bad session nonce
 */
public class ExExternalAuthFailure extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExExternalAuthFailure() { super(); }
    public ExExternalAuthFailure(PBException pb) { super(pb); }

    @Override
    public Type getWireType()
    {
        return Type.EXTERNAL_AUTH_FAILURE;
    }
}
