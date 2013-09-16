package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

public class EISetAttr extends AbstractEBIMC
{

    public final Path _path;
    public final Integer _flags;

    public EISetAttr(IIMCExecutor imce, Path path, Integer flags)
    {
        super(imce);

        _path = path;
        _flags = flags;
    }
}
