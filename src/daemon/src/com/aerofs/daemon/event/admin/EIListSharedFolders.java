package com.aerofs.daemon.event.admin;

import java.util.Collection;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.proto.Ritual.PBSharedFolder;

public class EIListSharedFolders extends AbstractEBIMC
{
    public enum Filter {
        USER_ROOTS,
        SHARED_FOLDERS
    }

    public final Filter _filter;
    public Collection<PBSharedFolder> _sharedFolders;

    public EIListSharedFolders(Filter filter)
    {
        super(Core.imce());
        _filter = filter;
    }

    public void setResult_(Collection<PBSharedFolder> sharedFolders)
    {
        _sharedFolders = sharedFolders;
    }
}
