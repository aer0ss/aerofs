package com.aerofs.daemon.rest.event;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.RestObject;
import com.aerofs.lib.Path;


public class EIFileInfo extends AbstractRestEBIMC
{
    public final RestObject _object;

    public EIFileInfo(IIMCExecutor imce, UserID user, RestObject object)
    {
        super(imce, user);
        _object = object;
    }
}
