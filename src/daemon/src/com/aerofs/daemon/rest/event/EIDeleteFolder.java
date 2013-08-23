package com.aerofs.daemon.rest.event;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

public class EIDeleteFolder extends AbstractRestEBIMC
{
    public final Path _path;
    public final boolean _recurse;

    public EIDeleteFolder(IIMCExecutor imce, UserID user, String path, boolean recurse)
    {
        super(imce, user);
        _path = mkpath(path);
        _recurse = recurse;
    }
}
