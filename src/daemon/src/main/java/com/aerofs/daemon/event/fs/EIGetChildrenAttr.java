package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

import java.util.List;

public class EIGetChildrenAttr extends AbstractEBIMC
{
    public final Path _path;
    public List<OA> _oas;

    public EIGetChildrenAttr(Path path, IIMCExecutor imce)
    {
        super(imce);
        _path = path;
    }

    public void setResult_(List<OA> oas)
    {
        _oas = oas;
    }
}
