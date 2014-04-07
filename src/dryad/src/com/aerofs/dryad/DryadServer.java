package com.aerofs.dryad;

import com.aerofs.base.DefaultUncaughtExceptionHandler;
import com.aerofs.base.Loggers;
import com.aerofs.dryad.persistence.IDryadPersistence;
import com.aerofs.dryad.persistence.LocalFileBasedPersistence;
import com.aerofs.dryad.providers.ExFormatErrorExceptionMapper;
import com.aerofs.dryad.resources.ApplianceLogsResource;
import com.aerofs.dryad.resources.ClientLogsResource;
import com.aerofs.restless.Configuration;
import com.aerofs.restless.Service;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import java.io.File;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetSocketAddress;
import java.util.Set;

import static java.util.concurrent.Executors.newCachedThreadPool;

public class DryadServer extends Service
{
    static
    {
        Loggers.init();
    }

    public DryadServer(Injector injector)
    {
        super("dryad-upload", new InetSocketAddress("0.0.0.0", 4433), null, injector,
                newCachedThreadPool());

        addResource(ApplianceLogsResource.class);
        addResource(ClientLogsResource.class);
    }

    public static void main(String[] args) throws Exception
    {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler()
        {
            @Override
            public void uncaughtException(Thread t, Throwable e)
            {
                System.err.println("Uncaught exception thd:" + t.getName() + " err:" + e);
                e.printStackTrace(System.err);
                System.exit(1);
            }
        });

        if (args.length != 1) {
            System.err.print("Usage: java -jar dryad.jar <dryad_properties>");
            System.exit(1);
        }

        Thread.setDefaultUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler());

        Injector injector = Guice.createInjector(new DryadModule(args[0]));

        final DryadServer dryad = new DryadServer(injector);
        dryad.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run()
            {
                dryad.stop();
            }
        });
    }

    @Override
    protected Set<Class<?>> singletons()
    {
        return ImmutableSet.<Class<?>>of(ExFormatErrorExceptionMapper.class);
    }

    private static class DryadModule extends AbstractModule
    {
        private final DryadProperties _properties;

        public DryadModule(String propertiesSource)
                throws IOException
        {
            _properties = new DryadProperties(new File(propertiesSource));
        }

        @Override
        protected void configure()
        {
            bind(DryadProperties.class).toInstance(_properties);
            bind(Configuration.class).to(DryadConfiguration.class);
            bind(IDryadPersistence.class).to(LocalFileBasedPersistence.class);
        }
    }
}
