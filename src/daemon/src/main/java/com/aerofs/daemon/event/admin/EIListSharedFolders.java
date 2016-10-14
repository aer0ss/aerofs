package com.aerofs.daemon.event.admin;

import java.util.Collection;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.proto.Ritual.PBSharedFolder;

public class EIListSharedFolders extends AbstractEBIMC
{
    public Collection<PBSharedFolder> _sharedFolders;

    public EIListSharedFolders()
    {
        super(Core.imce());
    }

    public void setResult_(Collection<PBSharedFolder> sharedFolders)
    {
        _sharedFolders = sharedFolders;
    }
}
