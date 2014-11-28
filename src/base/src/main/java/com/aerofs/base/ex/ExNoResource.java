/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

/**
 * This exception can be thrown when a needed _internal_ resource
 * (queues, workers, etc.) is unavailable or oversubscribed.
 */
public class ExNoResource extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExNoResource()
    {
        super();
    }

    public ExNoResource(String msg)
    {
        super(msg);
    }

    public ExNoResource(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NO_RESOURCE;
    }
}
