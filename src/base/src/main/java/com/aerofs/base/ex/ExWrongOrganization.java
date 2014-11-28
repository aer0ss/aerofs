package com.aerofs.base.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExWrongOrganization extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExWrongOrganization()
    {
        super();
    }

    public ExWrongOrganization(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.WRONG_ORGANIZATION;
    }
}
