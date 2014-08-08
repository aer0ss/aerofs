package com.aerofs.dryad;

import com.aerofs.base.DefaultUncaughtExceptionHandler;
import com.aerofs.base.Loggers;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.dryad.Blacklist.DeviceBlacklist;
import com.aerofs.dryad.Blacklist.UserBlacklist;
import com.aerofs.dryad.providers.DryadExceptionMapper;
import com.aerofs.dryad.resources.LogsResource;
import com.aerofs.restless.Configuration;
import com.aerofs.restless.Service;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import java.io.File;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetSocketAddress;
import java.util.Set;

import static com.aerofs.dryad.DryadProperties.SERVER_HOSTNAME;
import static com.aerofs.dryad.DryadProperties.SERVER_PORT;
import static java.util.concurrent.Executors.newCachedThreadPool;

public class DryadServer extends Service
{
    private DryadServer(Injector injector, DryadProperties properties)
    {
        super("dryad-upload", getServerAddressFromProperties(properties), null, injector,
                newCachedThreadPool());

        addResource(LogsResource.class);
    }

    private static InetSocketAddress getServerAddressFromProperties(DryadProperties properties)
            throws NumberFormatException
    {
        String hostname = properties.getProperty(SERVER_HOSTNAME);
        int port = Integer.valueOf(properties.getProperty(SERVER_PORT));
        return new InetSocketAddress(hostname, port);
    }

    private static UncaughtExceptionHandler printToStderr()
    {
        return new UncaughtExceptionHandler()
        {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable)
            {
                System.err.println("Uncaught exception thd: " + thread.getName() +
                        ", err: " + throwable);
                throwable.printStackTrace(System.err);
                System.exit(1);
            }
        };
    }

    public static void main(String[] args) throws Exception
    {
        Thread.setDefaultUncaughtExceptionHandler(printToStderr());

        if (args.length != 1) {
            System.err.print("Usage: java -jar dryad.jar <dryad_properties>");
            System.exit(1);
        }

        Loggers.init();
        Thread.setDefaultUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler());
        ProgramBanner.logBanner("banner.txt");

        File propertiesFile = new File(args[0]);
        DryadProperties properties = DryadProperties.loadFromFile(propertiesFile);
        ConfigurationProperties.setProperties(properties);

        DryadModule module = new DryadModule(properties);
        Injector injector = Guice.createInjector(module);

        final DryadServer dryad = new DryadServer(injector, properties);
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
        return ImmutableSet.<Class<?>>of(
                LogStore.class,
                DryadExceptionMapper.class,
                UserBlacklist.class,
                DeviceBlacklist.class);
    }

    private static class DryadModule extends AbstractModule
    {
        private final DryadProperties _properties;

        public DryadModule(DryadProperties properties)
        {
            _properties = properties;
        }

        @Override
        protected void configure()
        {
            bind(DryadProperties.class).toInstance(_properties);
            bind(Configuration.class).to(DryadConfiguration.class);
        }
    }
}
