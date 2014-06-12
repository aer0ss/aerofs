/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.base.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExRateLimitExceeded extends AbstractExWirable
{
    private static final long serialVersionUID = 0;

    @Override
    public Type getWireType()
    {
        return Type.RATE_LIMIT_EXCEEDED;
    }

    public ExRateLimitExceeded()
    {
        super();
    }

    public ExRateLimitExceeded(PBException pb)
    {
        super(pb);
    }
}
