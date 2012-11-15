/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.sumu.singleuser;

import com.aerofs.daemon.core.store.IStores;
import com.google.inject.AbstractModule;
import com.google.inject.internal.Scoping;

public class SingleuserModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        binder().disableCircularProxies();

        bind(IStores.class).to(SingleuserStores.class);
    }
}
