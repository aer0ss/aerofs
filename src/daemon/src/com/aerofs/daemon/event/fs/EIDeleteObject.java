package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.UserID;

public class EIDeleteObject extends AbstractEIFS
{
    public final Path _path;

    public EIDeleteObject(UserID user, IIMCExecutor imce, Path path)
    {
        super(user, imce);
        _path = path;
    }
}
