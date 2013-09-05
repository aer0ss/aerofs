package com.aerofs.daemon.rest.event;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

public class EIListRoots extends AbstractRestEBIMC
{
    public EIListRoots(IIMCExecutor imce, UserID user)
    {
        super(imce, user);
    }
}
