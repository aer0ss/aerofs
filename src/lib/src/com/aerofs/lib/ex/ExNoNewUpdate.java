package com.aerofs.lib.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExNoNewUpdate extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;


    public ExNoNewUpdate()
    {
        super();
    }

    public ExNoNewUpdate(String string)
    {
        super(string);
    }

    public ExNoNewUpdate(Exception cause)
    {
        super(cause);
    }

    public ExNoNewUpdate(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NO_NEW_UPDATE;
    }
}
