/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

/**
 * Thrown by server code when a necessary external resource is unavailable.
 * Example: an external server used for authentication is down or misconfigured.
 */
public class ExExternalServiceUnavailable extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExExternalServiceUnavailable(String message) { super(message); }

    public ExExternalServiceUnavailable(PBException pb) { super(pb); }

    @Override
    public Type getWireType()
    {
        return Type.EXTERNAL_SERVICE_UNAVAILABLE;
    }
}
