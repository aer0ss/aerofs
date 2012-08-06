package com.aerofs.lib.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExNoPerm extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExNoPerm()
    {
        super();
    }

    public ExNoPerm(String string)
    {
        super(string);
    }

    public ExNoPerm(Exception e)
    {
        super(e);
    }

    public ExNoPerm(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NO_PERM;
    }
}
