/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExDeviceIDAlreadyExist extends AbstractExWirable
{
    private static final long serialVersionUID = 0L;

    public ExDeviceIDAlreadyExist()
    {
        super();
    }

    public ExDeviceIDAlreadyExist(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.DEVICE_ID_ALREADY_EXIST;
    }
}
