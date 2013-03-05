package com.aerofs.daemon.core.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExNoAvailDevice extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExNoAvailDevice()
    {
        super();
    }

    public ExNoAvailDevice(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NO_AVAIL_DEVICE;
    }
}
