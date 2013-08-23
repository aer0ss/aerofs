package com.aerofs.daemon.rest.event;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

public class EIListChildren extends AbstractRestEBIMC
{
    public final Path _path;

    public EIListChildren(IIMCExecutor imce, UserID user, String path)
    {
        super(imce, user);
        _path = mkpath(path);
    }
}
