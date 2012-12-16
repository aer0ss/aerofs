/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExAlreadyInvited extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExAlreadyInvited()
    {
        super();
    }

    public ExAlreadyInvited(String msg)
    {
        super(msg);
    }

    public ExAlreadyInvited(Throwable cause)
    {
        super(cause);
    }

    public ExAlreadyInvited(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.ALREADY_INVITED;
    }
}
