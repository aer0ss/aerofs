package com.aerofs.daemon.rest.event;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.RestObject;

public class EIListChildren extends AbstractRestEBIMC
{
    public final RestObject _object;

    public EIListChildren(IIMCExecutor imce, UserID user, RestObject object)
    {
        super(imce, user);
        _object = object;
    }
}
