package com.aerofs.daemon.core;

import com.google.inject.Singleton;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

@Singleton
public class CoreIMCExecutor
{
    private final IIMCExecutor _imce;

    public CoreIMCExecutor(IIMCExecutor imce)
    {
        _imce = imce;
    }

    public IIMCExecutor imce()
    {
        return _imce;
    }
}
