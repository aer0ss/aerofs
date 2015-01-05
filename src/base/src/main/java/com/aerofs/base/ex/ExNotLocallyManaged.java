package com.aerofs.base.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExNotLocallyManaged extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExNotLocallyManaged()
    {
        super();
    }

    public ExNotLocallyManaged(String str)
    {
        super(str);
    }

    public ExNotLocallyManaged(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NOT_LOCALLY_MANAGED;
    }
}
