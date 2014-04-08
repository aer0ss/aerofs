package com.aerofs.dryad;

import com.aerofs.base.Loggers;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.dryad.resources.LogsResource;
import com.aerofs.restless.Configuration;
import com.aerofs.restless.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import org.slf4j.Logger;

import java.io.FileInputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetSocketAddress;
import java.util.Properties;

public class Dryad extends Service
{
    static
    {
        Loggers.init();
    }

    private static final Logger l = Loggers.getLogger(Dryad.class);

    public Dryad(Injector injector)
    {
        super("dryad", new InetSocketAddress("0.0.0.0", 4433), null, injector, null);
        addResource(LogsResource.class);
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

        Properties extra = new Properties();
        if (args.length > 0) {
            extra.load(new FileInputStream(args[0]));
        }

        ConfigurationProperties.setProperties(extra);

        Injector injector = Guice.createInjector(Stage.PRODUCTION, dryadModule());
        final Dryad dryad = new Dryad(injector);
        dryad.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run()
            {
                dryad.stop();
            }
        });
    }

    public static Module dryadModule()
    {
        return new AbstractModule() {
            @Override
            protected void configure()
            {
                bind(Configuration.class).to(DryadConfiguration.class);
            }
        };
    }
}
