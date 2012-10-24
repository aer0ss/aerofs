package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

public class EIRelocateRootAnchor extends AbstractEBIMC
{
    public final String _newRootAnchor;

    public EIRelocateRootAnchor(String newRootAnchor, IIMCExecutor imce)
    {
        super(imce);
        _newRootAnchor = newRootAnchor;
    }
}
