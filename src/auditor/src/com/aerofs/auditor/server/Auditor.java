/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.auditor.resource.EventResource;
import com.aerofs.base.BaseParam.Audit;
import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.lib.properties.Configuration.Server;
import com.aerofs.restless.Configuration;
import com.aerofs.restless.Service;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;

import java.io.FileInputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.Set;

public class Auditor extends Service
{
    static
    {
        Loggers.init();
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            private static final int UNCAUGHT_EXCEPTION_EXIT_CODE = 99;
            @Override
            public void uncaughtException(Thread thread, Throwable throwable)
            {
                l.error("uncaught exception thd:{} err:{} - kill system",
                        thread.getName(), throwable, throwable);
                System.exit(UNCAUGHT_EXCEPTION_EXIT_CODE);
            }
        });
    }

    @Inject
    public Auditor(Injector injector, IPrivateKeyProvider kmgr)
    {
        super("auditor", new InetSocketAddress(Audit.SERVICE_PORT), kmgr, injector);
    }

    @Override
    protected Set<Class<?>> singletons()
    {
        // this weird invocation is necessary to confuse ImmutableSet.copyOf() with one element
        return ImmutableSet.copyOf(new Class<?>[]{EventResource.class});
    }

    public static void main(String[] args) throws Exception
    {
        Properties extra = new Properties();
        if (args.length > 0) extra.load(new FileInputStream(args[0]));

        Server.initialize(extra);

        // Note, we expect nginx or similar to provide ssl termination...
        new Auditor(Guice.createInjector(auditorModule()), null)
                .start();
    }

    static public Module auditorModule()
    {
        return (new AbstractModule() {
            @Override
            protected void configure()
            {
                bind(Configuration.class).to(AuditorConfiguration.class);
                bind(Downstream.IAuditChannel.class).toInstance(Downstream.create());
            }
        });
    }
}
