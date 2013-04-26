package com.aerofs.daemon.event.admin;

import java.util.Collection;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.lib.Path;
import com.aerofs.proto.Ritual.ListSharedFoldersReply.SharedFolder;

public class EIListSharedFolders extends AbstractEBIMC
{
    public Collection<SharedFolder> _sharedFolders;

    public EIListSharedFolders()
    {
        super(Core.imce());
    }

    public void setResult_(Collection<SharedFolder> sharedFolders)
    {
        _sharedFolders = sharedFolders;
    }
}
