package com.aerofs.lib.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExParentAlreadyShared extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExParentAlreadyShared()
    {
        super();
    }

    public ExParentAlreadyShared(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.PARENT_ALREADY_SHARED;
    }
}
