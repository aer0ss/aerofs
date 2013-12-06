/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.event;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.util.RestObject;
import com.aerofs.daemon.rest.util.EntityTagSet;

public class EIDeleteObject extends AbstractRestEBIMC
{
    public final RestObject _object;
    public final EntityTagSet _ifMatch;

    public EIDeleteObject(IIMCExecutor imce, UserID userid, RestObject object, EntityTagSet ifMatch)
    {
        super(imce, userid);
        _object = object;
        _ifMatch = ifMatch;
    }
}
