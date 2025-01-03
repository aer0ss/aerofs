/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.event;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.rest.auth.OAuthToken;
import com.aerofs.base.id.RestObject;
import com.aerofs.restless.util.EntityTagSet;

public class EIMoveObject extends AbstractRestEBIMC
{
    public final RestObject _object;
    public final RestObject _newParent;
    public final String _newName;
    public final EntityTagSet _ifMatch;

    public EIMoveObject(IIMCExecutor imce, OAuthToken token, RestObject object, String newParent,
            String newName, EntityTagSet ifMatch)
    {
        super(imce, token);
        _object = object;
        _newParent = RestObject.fromString(newParent);
        _newName = newName;
        _ifMatch = ifMatch;
    }
}
