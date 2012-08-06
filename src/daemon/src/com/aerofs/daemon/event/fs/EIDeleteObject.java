package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

public class EIDeleteObject extends AbstractEIFS
{
    public final Path _path;

    public EIDeleteObject(String user, IIMCExecutor imce, Path path)
    {
        super(user, imce);
        _path = path;
    }
}
