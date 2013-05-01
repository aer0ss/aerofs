/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.core.ds.AbstractPathResolver;
import com.aerofs.daemon.core.migration.IEmigrantTargetSIDLister;
import com.aerofs.daemon.core.migration.IEmigrantDetector;
import com.aerofs.daemon.core.migration.ImmigrantDetector;
import com.aerofs.daemon.core.multiplicity.multiuser.migration.MultiuserImmigrantDetector;
import com.aerofs.daemon.core.store.IStoreJoiner;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.Stores;
import com.aerofs.daemon.core.multiplicity.multiuser.migration.NullEmigrantTargetSIDLister;
import com.aerofs.daemon.core.multiplicity.multiuser.migration.NullEmigrantDetector;
import com.google.inject.AbstractModule;
import com.google.inject.internal.Scoping;
import org.slf4j.Logger;

import static com.aerofs.lib.guice.GuiceUtil.multibind;

public class MultiuserModule extends AbstractModule
{
    private static final Logger l = Loggers.getLogger(MultiuserModule.class);

    @Override
    protected void configure()
    {
        l.info("multi user mode");

        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        binder().disableCircularProxies();

        bind(IStores.class).to(Stores.class);
        bind(AbstractPathResolver.class).to(MultiuserPathResolver.class);
        multibind(binder(), ICoreEventHandlerRegistrar.class,
                MultiuserCoreEventHandlerRegistrar.class);

        bind(IEmigrantTargetSIDLister.class).to(NullEmigrantTargetSIDLister.class);
        bind(IEmigrantDetector.class).to(NullEmigrantDetector.class);
        bind(ImmigrantDetector.class).to(MultiuserImmigrantDetector.class);

        bind(IStoreJoiner.class).to(MultiuserStoreJoiner.class);
    }
}
