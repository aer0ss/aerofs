package com.aerofs.daemon.rest.event;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.rest.auth.OAuthToken;
import com.aerofs.base.id.RestObject;

public class EIListChildren extends AbstractRestEBIMC
{
    public final RestObject _object;
    // for compat with old /children resource
    public final boolean _includeParent;

    public EIListChildren(IIMCExecutor imce, OAuthToken token, RestObject object,
            boolean includeParent)
    {
        super(imce, token);
        _object = object;
        _includeParent = includeParent;
    }
}
