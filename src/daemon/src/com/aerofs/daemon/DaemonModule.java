package com.aerofs.daemon;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.internal.Scoping;

import com.aerofs.daemon.core.Core;

public class DaemonModule extends AbstractModule
{
    private final Injector _injCore;

    public DaemonModule(Injector injCore)
    {
        _injCore = injCore;
    }

    @Override
    protected void configure()
    {
        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);
    }

    @Provides
    public Core provideCore()
    {
        return _injCore.getInstance(Core.class);
    }
}
