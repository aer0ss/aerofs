/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.AbstractPathResolver;
import com.aerofs.daemon.core.migration.IEmigrantTargetSIDLister;
import com.aerofs.daemon.core.migration.IEmigrantDetector;
import com.aerofs.daemon.core.migration.IImmigrantCreator;
import com.aerofs.daemon.core.migration.IImmigrantDetector;
import com.aerofs.daemon.core.multiplicity.singleuser.migration.EmigrantTargetSIDLister;
import com.aerofs.daemon.core.store.IStoreJoiner;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.multiplicity.singleuser.migration.EmigrantDetector;
import com.aerofs.daemon.core.multiplicity.singleuser.migration.ImmigrantCreator;
import com.aerofs.daemon.core.multiplicity.singleuser.migration.ImmigrantDetector;
import com.google.inject.AbstractModule;
import com.google.inject.internal.Scoping;
import org.slf4j.Logger;

public class SingleuserModule extends AbstractModule
{
    private static final Logger l = Loggers.getLogger(SingleuserModule.class);

    @Override
    protected void configure()
    {
        l.info("single user mode");

        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        binder().disableCircularProxies();

        bind(IStores.class).to(SingleuserStores.class);
        bind(AbstractPathResolver.class).to(SingleuserPathResolver.class);

        bind(IEmigrantTargetSIDLister.class).to(EmigrantTargetSIDLister.class);
        bind(IEmigrantDetector.class).to(EmigrantDetector.class);
        bind(IImmigrantCreator.class).to(ImmigrantCreator.class);
        bind(IImmigrantDetector.class).to(ImmigrantDetector.class);

        bind(IStoreJoiner.class).to(SingleuserStoreJoiner.class);
    }
}
