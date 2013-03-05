/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExAlreadyExist extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExAlreadyExist()
    {
        super();
    }

    public ExAlreadyExist(String msg)
    {
        super(msg);
    }

    public ExAlreadyExist(Throwable cause)
    {
        super(cause);
    }

    public ExAlreadyExist(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.ALREADY_EXIST;
    }
}
