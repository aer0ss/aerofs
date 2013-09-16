/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.mobile;

import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.lib.guice.GuiceUtil;
import com.google.inject.AbstractModule;
import com.google.inject.internal.Scoping;

public class MobileModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        GuiceUtil.multibind(binder(), ICoreEventHandlerRegistrar.class,
                MobileCoreEventHandlerRegistar.class);
    }
}
