package com.aerofs.daemon.rest.event;

import com.aerofs.base.Version;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.util.RestObject;

public class EICreateObject extends AbstractRestEBIMC
{
    public final Version _version;
    public final RestObject _parent;
    public final String _name;
    public final boolean _folder;

    public EICreateObject(IIMCExecutor imce, UserID user, Version version,
            String parent, String name, boolean folder)
    {
        super(imce, user);
        _version = version;
        _parent = new RestObject(parent);
        _name = name;
        _folder = folder;
    }
}
