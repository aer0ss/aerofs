/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.event;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.util.RestObject;
import com.aerofs.daemon.rest.util.EntityTagSet;

public class EIMoveObject extends AbstractRestEBIMC
{
    public final RestObject _object;
    public final RestObject _newParent;
    public final String _newName;
    public final EntityTagSet _ifMatch;

    public EIMoveObject(IIMCExecutor imce, UserID userid, RestObject object, String newParent,
            String newName, EntityTagSet ifMatch)
    {
        super(imce, userid);
        _object = object;
        _newParent = new RestObject(newParent);
        _newName = newName;
        _ifMatch = ifMatch;
    }
}
