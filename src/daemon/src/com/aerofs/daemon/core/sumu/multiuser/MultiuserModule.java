/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.sumu.multiuser;

import com.aerofs.daemon.core.store.IStores;
import com.google.inject.AbstractModule;
import com.google.inject.internal.Scoping;

public class MultiuserModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        binder().disableCircularProxies();

        bind(IStores.class).to(MultiuserStores.class);
    }
}
