package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.UserID;

public class EIDeleteBranch extends AbstractEIFS
{
    public final Path _path;
    public final KIndex _kidx;

    public EIDeleteBranch(UserID user, IIMCExecutor imce, Path path, KIndex kidx)
    {
        super(user, imce);
        _path = path;
        _kidx = kidx;
    }
}
