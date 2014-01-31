/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.event;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.util.OAuthToken;
import com.aerofs.daemon.rest.util.RestObject;
import com.aerofs.restless.util.EntityTagSet;

public class EIDeleteObject extends AbstractRestEBIMC
{
    public final RestObject _object;
    public final EntityTagSet _ifMatch;

    public EIDeleteObject(IIMCExecutor imce, OAuthToken token, RestObject object,
            EntityTagSet ifMatch)
    {
        super(imce, token);
        _object = object;
        _ifMatch = ifMatch;
    }
}
