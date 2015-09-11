package com.aerofs.base.ex;

import com.aerofs.proto.Common;

public class ExPasswordExpired extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExPasswordExpired()
    {
        super();
    }

    public ExPasswordExpired(String string)
    {
        super(string);
    }

    public ExPasswordExpired(Exception e)
    {
        super(e);
    }

    public ExPasswordExpired(Common.PBException pb)
    {
        super(pb);
    }

    @Override
    public Common.PBException.Type getWireType()
    {
        return Common.PBException.Type.PASSWORD_EXPIRED;
    }
}
