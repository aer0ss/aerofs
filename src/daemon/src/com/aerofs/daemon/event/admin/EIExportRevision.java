package com.aerofs.daemon.event.admin;

import java.io.File;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

public class EIExportRevision extends AbstractEBIMC {

    public final Path _path;
    public final byte[] _index;
    public File _dst;

    public EIExportRevision(IIMCExecutor imce, Path path, byte[] index)
    {
        super(imce);
        _path = path;
        _index = index;
    }

    public void setResult_(File dst)
    {
        _dst = dst;
    }
}
