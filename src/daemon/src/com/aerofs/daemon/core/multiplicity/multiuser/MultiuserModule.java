/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.core.ds.IPathResolver;
import com.aerofs.daemon.core.migration.IEmigrantCreator;
import com.aerofs.daemon.core.migration.IEmigrantDetector;
import com.aerofs.daemon.core.migration.IImmigrantCreator;
import com.aerofs.daemon.core.migration.IImmigrantDetector;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.Stores;
import com.aerofs.daemon.core.multiplicity.multiuser.migration.NullEmigrantCreator;
import com.aerofs.daemon.core.multiplicity.multiuser.migration.NullEmigrantDetector;
import com.aerofs.daemon.core.multiplicity.multiuser.migration.NullImmigrantCreator;
import com.aerofs.daemon.core.multiplicity.multiuser.migration.NullImmigrantDetector;
import com.aerofs.lib.Util;
import com.google.inject.AbstractModule;
import com.google.inject.internal.Scoping;

import static com.aerofs.lib.guice.GuiceUtil.multiBind;

public class MultiuserModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        Util.l(this).info("multiuser mode");

        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        binder().disableCircularProxies();

        bind(IStores.class).to(Stores.class);
        bind(IPathResolver.class).to(MultiuserPathResolver.class);
        multiBind(binder(), ICoreEventHandlerRegistrar.class,
                MultiuserCoreEventHandlerRegistrar.class);

        bind(IEmigrantCreator.class).to(NullEmigrantCreator.class);
        bind(IEmigrantDetector.class).to(NullEmigrantDetector.class);
        bind(IImmigrantCreator.class).to(NullImmigrantCreator.class);
        bind(IImmigrantDetector.class).to(NullImmigrantDetector.class);
    }
}
