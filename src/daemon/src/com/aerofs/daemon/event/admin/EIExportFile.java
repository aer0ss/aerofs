package com.aerofs.daemon.event.admin;

import java.io.File;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

public final class EIExportFile extends AbstractEBIMC
{
    public final Path _src;
    public File _dst;

    public EIExportFile(IIMCExecutor imce, Path src)
    {
        super(imce);
        _src = src;
    }

    public void setResult_(File dst)
    {
        _dst = dst;
    }

}
