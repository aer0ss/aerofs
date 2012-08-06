package com.aerofs.lib.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExProtocolError extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExProtocolError()
    {
        super();
    }

    public ExProtocolError(String msg)
    {
        super(msg);
    }

    public ExProtocolError(Class<?> c)
    {
        super("field " + c + " is missing or has incorrect value");
    }

    public ExProtocolError(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.PROTOCOL_ERROR;
    }
}
