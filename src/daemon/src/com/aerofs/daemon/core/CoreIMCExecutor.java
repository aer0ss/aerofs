package com.aerofs.daemon.core;

import com.google.inject.Inject;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

public class CoreIMCExecutor
{
    private final IIMCExecutor _imce;

    @Inject
    public CoreIMCExecutor(IIMCExecutor imce)
    {
        _imce = imce;
    }

    public IIMCExecutor imce()
    {
        return _imce;
    }
}
