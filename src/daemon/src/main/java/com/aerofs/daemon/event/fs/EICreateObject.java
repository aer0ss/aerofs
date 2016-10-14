package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

public class EICreateObject extends AbstractEBIMC
{
    public final Path _path;
    public final boolean _dir;
    public boolean _exist;

    public EICreateObject(IIMCExecutor imce, Path path, boolean isDir)
    {
        super(imce);
        _path = path;
        _dir = isDir;
    }

    public void setResult_(boolean exist)
    {
        _exist = exist;
    }
}
