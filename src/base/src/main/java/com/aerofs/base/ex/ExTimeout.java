/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExTimeout extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExTimeout()
    {
        super();
    }

    public ExTimeout(String string)
    {
        super(string);
    }

    public ExTimeout(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.TIMEOUT;
    }
}
