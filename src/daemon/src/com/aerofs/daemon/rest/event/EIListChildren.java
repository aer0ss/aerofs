package com.aerofs.daemon.rest.event;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.util.OAuthToken;
import com.aerofs.daemon.rest.util.RestObject;

public class EIListChildren extends AbstractRestEBIMC
{
    public final RestObject _object;

    public EIListChildren(IIMCExecutor imce, OAuthToken token, RestObject object)
    {
        super(imce, token);
        _object = object;
    }
}
