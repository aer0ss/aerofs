package com.aerofs.lib.ex;


import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExDeviceOffline extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExDeviceOffline()
    {
        super();
    }

    public ExDeviceOffline(String string)
    {
        super(string);
    }

    public ExDeviceOffline(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.DEVICE_OFFLINE;
    }
}
