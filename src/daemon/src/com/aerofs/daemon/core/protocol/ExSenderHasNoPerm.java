package com.aerofs.daemon.core.protocol;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExSenderHasNoPerm extends AbstractExWirable
{
    private static final long serialVersionUID = 0;

    public ExSenderHasNoPerm()
    {
    }

    /**
     * Do not remove. It's used by Exceptions.fromPB()
     */
    public ExSenderHasNoPerm(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.SENDER_HAS_NO_PERM;
    }
}
