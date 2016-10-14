package com.aerofs.lib.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExNotShared extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExNotShared()
    {
        super();
    }

    public ExNotShared(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NOT_SHARED;
    }
}
