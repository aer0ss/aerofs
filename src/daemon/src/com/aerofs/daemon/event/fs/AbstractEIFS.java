package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

public abstract class AbstractEIFS extends AbstractEBIMC
{
    private final String _user;

    protected AbstractEIFS(String user, IIMCExecutor imce)
    {
        super(imce);
        _user = user;
    }

    final public String user()
    {
        return _user;
    }
}
