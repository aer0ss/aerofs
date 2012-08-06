package com.aerofs.lib.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExOutOfSpace extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExOutOfSpace(String msg)
    {
        super(msg);
    }

    public ExOutOfSpace()
    {
        super();
    }

    public ExOutOfSpace(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.OUT_OF_SPACE;
    }
}
