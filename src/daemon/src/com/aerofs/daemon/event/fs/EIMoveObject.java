package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.base.id.UserID;

public class EIMoveObject extends AbstractEIFS
{
    public final Path _from;
    public final Path _toParent;
    public final String _toName;

    public EIMoveObject(UserID user, IIMCExecutor imce, Path from, Path toParent, String toName)
    {
        super(user, imce);
        _from = from;
        _toParent = toParent;
        _toName = toName;
    }
}
