package com.aerofs.daemon.rest;

import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.lib.guice.GuiceUtil;
import com.aerofs.restless.Configuration;
import com.google.inject.AbstractModule;
import com.google.inject.internal.Scoping;

public class RestModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        GuiceUtil.multibind(binder(), ICoreEventHandlerRegistrar.class,
                RestCoreEventHandlerRegistar.class);

        bind(Configuration.class).to(RestConfiguration.class);
    }
}
