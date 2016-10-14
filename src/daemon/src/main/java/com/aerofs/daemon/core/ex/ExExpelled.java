package com.aerofs.daemon.core.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExExpelled extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExExpelled()
    {
        super();
    }

    public ExExpelled(String msg)
    {
        super(msg);
    }

    public ExExpelled(Throwable cause)
    {
        super(cause);
    }

    public ExExpelled(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.EXPELLED;
    }
}
