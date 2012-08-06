package com.aerofs.daemon.event.admin;

import java.util.Collection;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.lib.Path;

public class EIListSharedFolders extends AbstractEBIMC
{
    public Collection<Path> _paths;

    public EIListSharedFolders()
    {
        super(Core.imce());
    }

    public void setResult_(Collection<Path> paths)
    {
        _paths = paths;
    }

}
