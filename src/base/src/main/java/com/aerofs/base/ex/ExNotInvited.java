package com.aerofs.base.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;
/**
 * Created by elvisy on 10/30/15.
 */
public class ExNotInvited extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExNotInvited()
    {
        super();
    }

    public ExNotInvited(String string)
    {
        super(string);
    }

    public ExNotInvited(Exception e)
    {
        super(e);
    }

    public ExNotInvited(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NO_INVITE;
    }
}
