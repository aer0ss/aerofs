package com.aerofs.daemon.core.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.ex.ISuppressStack;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExAborted extends AbstractExWirable implements ISuppressStack
{
    private static final long serialVersionUID = 1L;

    public ExAborted()
    {
        super();
    }

    public ExAborted(String string)
    {
        super(string);
    }

    public ExAborted(Throwable cause)
    {
        super(cause);
    }

    public ExAborted(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.ABORTED;
    }
}
