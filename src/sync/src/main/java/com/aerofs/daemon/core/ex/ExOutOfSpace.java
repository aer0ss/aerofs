package com.aerofs.daemon.core.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExOutOfSpace extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

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
