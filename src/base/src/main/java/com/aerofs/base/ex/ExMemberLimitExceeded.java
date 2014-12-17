/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.base.ex;

import com.aerofs.proto.Common.PBException.Type;

public class ExMemberLimitExceeded extends AbstractExWirable
{

    private static final long serialVersionUID = -584715074847770115L;

    public ExMemberLimitExceeded()
    {
        super();
    }

    public ExMemberLimitExceeded(String msg)
    {
        super(msg);
    }

    @Override
    public Type getWireType()
    {
        return Type.MEMBER_LIMIT_EXCEEDED;
    }
}
