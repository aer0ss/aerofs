/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.auditor.resource.EventResource;
import com.aerofs.auditor.resource.HttpRequestAuthenticator;
import com.aerofs.base.BaseParam.Audit;
import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import com.aerofs.lib.properties.Configuration.Server;
import com.aerofs.restless.Configuration;
import com.aerofs.restless.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

import java.io.FileInputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetSocketAddress;
import java.util.Properties;

public class Auditor extends Service
{
    static
    {
        Loggers.init();
    }

    @Inject
    public Auditor(Injector injector, IPrivateKeyProvider kmgr)
    {
        super("auditor", new InetSocketAddress(Audit.SERVICE_PORT), kmgr, injector);

        addResource(EventResource.class);
    }

    @Override
    public ChannelPipeline getSpecializedPipeline()
    {
        ChannelPipeline p = super.getSpecializedPipeline();
        p.addBefore(JERSEY_HANLDER, "auth", new HttpRequestAuthenticator());

        // the magic numbers following are just guesses and have not been well-tuned.
        // 8: core thread pool size
        // 1048576: max memory commitments for work queued to get into the pool
        p.addBefore(JERSEY_HANLDER, "execution", new ExecutionHandler(
                new OrderedMemoryAwareThreadPoolExecutor(8, 1048576, 1048576), false, true));
        return p;
    }

    public static void main(String[] args) throws Exception
    {
        setDefaultExceptionHandler();

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

    static private void setDefaultExceptionHandler()
    {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable)
            {
                l.error("uncaught exception thd:{} err:{}", thread.getName(), throwable, throwable);
                System.exit(UNCAUGHT_EXCEPTION_EXIT_CODE);
            }
        });
    }

    private static final int UNCAUGHT_EXCEPTION_EXIT_CODE = 99;
}
