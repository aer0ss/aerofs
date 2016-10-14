package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

public class EIDeleteObject extends AbstractEBIMC
{
    public final Path _path;

    public EIDeleteObject(IIMCExecutor imce, Path path)
    {
        super(imce);
        _path = path;
    }
}
