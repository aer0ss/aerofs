package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.UserID;

public class EISetAttr extends AbstractEIFS
{

    public final Path _path;
    public final Integer _flags;

    public EISetAttr(UserID user, IIMCExecutor imce, Path path, Integer flags)
    {
        super(user, imce);

        _path = path;
        _flags = flags;
    }
}
