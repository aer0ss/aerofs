package com.aerofs.daemon.rest.event;

import com.aerofs.base.Version;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.util.OAuthToken;
import com.aerofs.daemon.rest.util.RestObject;

public class EICreateObject extends AbstractRestEBIMC
{
    public final Version _version;
    public final RestObject _parent;
    public final String _name;
    public final boolean _folder;

    public EICreateObject(IIMCExecutor imce, OAuthToken token, Version version,
            String parent, String name, boolean folder)
    {
        super(imce, token);
        _version = version;
        _parent = new RestObject(parent);
        _name = name;
        _folder = folder;
    }
}
