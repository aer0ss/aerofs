package com.aerofs.lib.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExNotDir extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExNotDir()
    {
        super();
    }

    public ExNotDir(String msg)
    {
        super(msg);
    }

    public ExNotDir(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NOT_DIR;
    }
}
