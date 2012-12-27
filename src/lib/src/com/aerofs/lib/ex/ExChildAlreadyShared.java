package com.aerofs.lib.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExChildAlreadyShared extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExChildAlreadyShared()
    {
        super();
    }

    public ExChildAlreadyShared(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.CHILD_ALREADY_SHARED;
    }
}
