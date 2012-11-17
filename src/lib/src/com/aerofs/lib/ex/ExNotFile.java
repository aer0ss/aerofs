package com.aerofs.lib.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExNotFile extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExNotFile()
    {
        super();
    }

    public ExNotFile(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NOT_FILE;
    }
}
