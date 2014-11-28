/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExEmptyEmailAddress extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExEmptyEmailAddress()
    {
        super();
    }

    public ExEmptyEmailAddress(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.EMPTY_EMAIL_ADDRESS;
    }
}
