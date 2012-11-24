package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.id.UserID;

public abstract class AbstractEIFS extends AbstractEBIMC
{
    private final UserID _user;

    protected AbstractEIFS(UserID user, IIMCExecutor imce)
    {
        super(imce);
        _user = user;
    }

    final public UserID user()
    {
        return _user;
    }
}
