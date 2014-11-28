/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExBadArgs extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExBadArgs()
    {
        super();
    }

    public ExBadArgs(String msg)
    {
        super(msg);
    }

    public ExBadArgs(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.BAD_ARGS;
    }
}
