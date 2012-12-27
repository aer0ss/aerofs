/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExDeviceIDAlreadyExists extends AbstractExWirable
{
    private static final long serialVersionUID = 0L;

    public ExDeviceIDAlreadyExists()
    {
        super();
    }

    public ExDeviceIDAlreadyExists(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.DEVICE_ID_ALREADY_EXISTS;
    }
}
