package com.aerofs.daemon.rest.event;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.util.OAuthToken;
import com.aerofs.daemon.rest.util.RestObject;

public class EIObjectInfo extends AbstractRestEBIMC
{
    public final RestObject _object;
    public final Type _type;

    public enum Type
    {
        FILE,
        FOLDER
    }

    public EIObjectInfo(IIMCExecutor imce, OAuthToken token, RestObject object, Type type)
    {
        super(imce, token);
        _object = object;
        _type = type;
    }
}
