package com.aerofs.dryad;

import com.aerofs.base.Loggers;

import com.aerofs.dryad.providers.ExFormatErrorExceptionMapper;
import com.aerofs.dryad.resources.ApplianceLogsResource;
import com.aerofs.dryad.resources.ClientLogsResource;
import com.aerofs.dryad.storage.ApplianceLogsDryadDatabase;
import com.aerofs.dryad.storage.ClientLogsDryadDatabase;
import com.aerofs.restless.Configuration;
import com.aerofs.restless.Service;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import java.io.FileInputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.Set;

public class DryadServer extends Service
{
    static
    {
        Loggers.init();
    }

    public DryadServer(Injector injector, ApplianceLogsDryadDatabase ad, ClientLogsDryadDatabase cd)
    {
        super("dryad-upload", new InetSocketAddress("0.0.0.0", 4433), null, injector, null);

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

        Properties properties = new Properties();
        properties.load(new FileInputStream(args[0]));

        String storageDirectory = properties.getProperty("dryad.storage.directory");

        ApplianceLogsDryadDatabase ad = new ApplianceLogsDryadDatabase(storageDirectory);
        ClientLogsDryadDatabase cd = new ClientLogsDryadDatabase(storageDirectory);

        Injector injector = Guice.createInjector(dryadModule(ad, cd));

        final DryadServer dryad = new DryadServer(injector, ad, cd);
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

    public static Module dryadModule(final ApplianceLogsDryadDatabase ad,
            final ClientLogsDryadDatabase cd)
    {
        return new AbstractModule() {
            @Override
            protected void configure()
            {
                bind(Configuration.class).to(DryadConfiguration.class);
                bind(ApplianceLogsResource.class).toInstance(new ApplianceLogsResource(ad));
                bind(ClientLogsResource.class).toInstance(new ClientLogsResource(cd));
            }
        };
    }
}