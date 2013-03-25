/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExNoAdmin extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExNoAdmin(String msg)
    {
        super(msg);
    }

    public ExNoAdmin(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NO_ADMIN;
    }
}
