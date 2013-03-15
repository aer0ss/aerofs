/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExNoAdminForNonEmptyTeam extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExNoAdminForNonEmptyTeam(String msg)
    {
        super(msg);
    }

    public ExNoAdminForNonEmptyTeam(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NO_ADMIN_FOR_NON_EMPTY_TEAM;
    }
}
