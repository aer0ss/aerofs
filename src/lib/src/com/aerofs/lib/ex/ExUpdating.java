/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExUpdating extends AbstractExWirable
{
    private static final long serialVersionUID = 0;

    public ExUpdating()
    {}

    public ExUpdating(PBException e)
    {}

    @Override
    public Type getWireType()
    {
        return Type.UPDATING;
    }
}
