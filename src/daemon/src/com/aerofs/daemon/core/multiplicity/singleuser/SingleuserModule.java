/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.daemon.core.ds.IPathResolver;
import com.aerofs.daemon.core.migration.IEmigrantCreator;
import com.aerofs.daemon.core.migration.IEmigrantDetector;
import com.aerofs.daemon.core.migration.IImmigrantCreator;
import com.aerofs.daemon.core.migration.IImmigrantDetector;
import com.aerofs.daemon.core.store.IStoreJoiner;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.multiplicity.singleuser.migration.EmigrantCreator;
import com.aerofs.daemon.core.multiplicity.singleuser.migration.EmigrantDetector;
import com.aerofs.daemon.core.multiplicity.singleuser.migration.ImmigrantCreator;
import com.aerofs.daemon.core.multiplicity.singleuser.migration.ImmigrantDetector;
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
        bind(IPathResolver.class).to(SingleuserPathResolver.class);

        bind(IEmigrantCreator.class).to(EmigrantCreator.class);
        bind(IEmigrantDetector.class).to(EmigrantDetector.class);
        bind(IImmigrantCreator.class).to(ImmigrantCreator.class);
        bind(IImmigrantDetector.class).to(ImmigrantDetector.class);

        bind(IStoreJoiner.class).to(SingleuserStoreJoiner.class);
    }
}
