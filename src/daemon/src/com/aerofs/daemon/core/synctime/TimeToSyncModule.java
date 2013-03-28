/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.synctime;

import com.aerofs.base.Loggers;
import com.google.inject.AbstractModule;
import com.google.inject.internal.Scoping;
import org.slf4j.Logger;

public class TimeToSyncModule extends AbstractModule
{
    private static final Logger l = Loggers.getLogger(TimeToSyncModule.class);

    @Override
    protected void configure()
    {
        l.info("instantiate time-to-sync");

        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        binder().disableCircularProxies();

        bind(TimeToSyncCollector.class).asEagerSingleton();
        bind(TimeToSyncHistogram.class).to(SelfReportingTimeToSyncHistogram.class);
    }
}
