package com.aerofs.base.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExBadCredential extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExBadCredential()
    {
        super();
    }

    public ExBadCredential(String string)
    {
        super(string);
    }

    public ExBadCredential(Exception e)
    {
        super(e);
    }

    public ExBadCredential(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.BAD_CREDENTIAL;
    }
}
