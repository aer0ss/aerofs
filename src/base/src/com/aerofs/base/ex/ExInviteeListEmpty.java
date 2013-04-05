/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ex;

import com.aerofs.proto.Common.PBException.Type;

public class ExInviteeListEmpty extends AbstractExWirable
{
    private static final long serialVersionUID = 0L;

    @Override
    public Type getWireType()
    {
        return Type.INVITEE_LIST_EMPTY;
    }
}
