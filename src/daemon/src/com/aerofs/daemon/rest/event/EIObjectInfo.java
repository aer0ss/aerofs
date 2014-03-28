package com.aerofs.daemon.rest.event;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.util.Fields;
import com.aerofs.rest.util.AuthToken;
import com.aerofs.daemon.rest.util.RestObject;

public class EIObjectInfo extends AbstractRestEBIMC
{
    public final RestObject _object;
    public final Type _type;
    public final Fields _fields;

    public enum Type
    {
        FILE,
        FOLDER
    }

    public EIObjectInfo(IIMCExecutor imce, AuthToken token, RestObject object, Type type,
            Fields fields)
    {
        super(imce, token);
        _object = object;
        _type = type;
        _fields = fields;
    }
}
