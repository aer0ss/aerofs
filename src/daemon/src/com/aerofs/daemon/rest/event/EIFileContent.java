package com.aerofs.daemon.rest.event;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.RestObject;

public class EIFileContent extends AbstractRestEBIMC
{
    public final RestObject _object;

    public EIFileContent(IIMCExecutor imce, UserID userid, RestObject object)
    {
        super(imce, userid);
        _object = object;
    }
}
