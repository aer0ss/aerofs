/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

/**
 * Use this exception only if the user needs to log in
 */
public class ExNotAuthenticated extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExNotAuthenticated()
    {
        super();
    }

    public ExNotAuthenticated(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NOT_AUTHENTICATED;
    }
}
