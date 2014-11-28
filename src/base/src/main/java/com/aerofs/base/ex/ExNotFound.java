/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

/**
 * A generic wirable exception which indicates that a particular
 * resource was not found.
 */
public class ExNotFound extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExNotFound()
    {
        super();
    }

    public ExNotFound(String message)
    {
        super(message);
    }

    /**
     * Wraps the exception that caused this generic ExNotFound to be
     * thrown.
     *
     * TODO(AL): A specific exception is more suitable than wrapping an exception. Many callers
     * expect ExNotFound when they shouldn't, so for now we will wrap specific exceptions.
     *
     * @param cause The cause of this ExNotFound
     */
    public ExNotFound(Throwable cause)
    {
        super(cause);
    }

    public ExNotFound(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NOT_FOUND;
    }
}
