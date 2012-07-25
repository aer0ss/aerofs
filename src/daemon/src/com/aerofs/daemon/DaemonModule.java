package com.aerofs.daemon;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.internal.Scoping;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.core.CoreIMCExecutor;

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

    @Provides
    public CoreIMCExecutor provideCoreIMCExecutor(Core core)
    {
        return new CoreIMCExecutor(Core.imce());
    }
}
