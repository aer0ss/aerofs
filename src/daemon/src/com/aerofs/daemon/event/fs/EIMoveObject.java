package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

public class EIMoveObject extends AbstractEBIMC
{
    public final Path _from;
    public final Path _toParent;
    public final String _toName;

    public EIMoveObject(IIMCExecutor imce, Path from, Path toParent, String toName)
    {
        super(imce);
        _from = from;
        _toParent = toParent;
        _toName = toName;
    }
}
