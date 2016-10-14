/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.rest.event;

import com.aerofs.base.id.RestObject;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.rest.auth.OAuthToken;

public class EIObjectPath extends AbstractRestEBIMC
{
    public final RestObject _object;

    public EIObjectPath(IIMCExecutor imce, OAuthToken token, RestObject object)
    {
        super(imce, token);
        _object = object;
    }
}
