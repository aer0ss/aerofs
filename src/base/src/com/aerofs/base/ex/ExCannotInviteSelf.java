/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ex;

import com.aerofs.proto.Common.PBException.Type;

public class ExCannotInviteSelf extends AbstractExWirable
{
    private static final long serialVersionUID = 0;

    @Override
    public Type getWireType()
    {
        return Type.CANNOT_INVITE_SELF;
    }
}
