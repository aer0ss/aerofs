package com.aerofs.base.ex;

import com.aerofs.proto.Common;

public class ExCannotReuseExistingPassword extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExCannotReuseExistingPassword()
    {
        super();
    }

    public ExCannotReuseExistingPassword(String string)
    {
        super(string);
    }

    public ExCannotReuseExistingPassword(Exception e)
    {
        super(e);
    }

    public ExCannotReuseExistingPassword(Common.PBException pb)
    {
        super(pb);
    }

    @Override
    public Common.PBException.Type getWireType()
    {
        return Common.PBException.Type.PASSWORD_ALREADY_EXIST;
    }

}