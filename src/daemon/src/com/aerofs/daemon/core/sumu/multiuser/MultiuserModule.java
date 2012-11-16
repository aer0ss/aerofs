/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.sumu.multiuser;

import com.aerofs.daemon.core.ds.IPathResolver;
import com.aerofs.daemon.core.migration.IEmigrantCreator;
import com.aerofs.daemon.core.migration.IEmigrantDetector;
import com.aerofs.daemon.core.migration.IImmigrantCreator;
import com.aerofs.daemon.core.migration.IImmigrantDetector;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.Stores;
import com.aerofs.daemon.core.sumu.multiuser.migration.NullEmigrantCreator;
import com.aerofs.daemon.core.sumu.multiuser.migration.NullEmigrantDetector;
import com.aerofs.daemon.core.sumu.multiuser.migration.NullImmigrantCreator;
import com.aerofs.daemon.core.sumu.multiuser.migration.NullImmigrantDetector;
import com.aerofs.lib.Util;
import com.google.inject.AbstractModule;
import com.google.inject.internal.Scoping;

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

        bind(IEmigrantCreator.class).to(NullEmigrantCreator.class);
        bind(IEmigrantDetector.class).to(NullEmigrantDetector.class);
        bind(IImmigrantCreator.class).to(NullImmigrantCreator.class);
        bind(IImmigrantDetector.class).to(NullImmigrantDetector.class);
    }
}
