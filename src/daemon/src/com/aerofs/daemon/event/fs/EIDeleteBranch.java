package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.Path;
import com.aerofs.base.id.UserID;

public class EIDeleteBranch extends AbstractEBIMC
{
    public final Path _path;
    public final KIndex _kidx;

    public EIDeleteBranch(IIMCExecutor imce, Path path, KIndex kidx)
    {
        super(imce);
        _path = path;
        _kidx = kidx;
    }
}
