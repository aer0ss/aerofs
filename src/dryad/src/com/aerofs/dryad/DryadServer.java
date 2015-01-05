package com.aerofs.dryad;

import com.aerofs.dryad.Blacklist.DeviceBlacklist;
import com.aerofs.dryad.Blacklist.UserBlacklist;
import com.aerofs.dryad.providers.DryadExceptionMapper;
import com.aerofs.dryad.resources.HealthCheckResource;
import com.aerofs.dryad.resources.LogsResource;
import com.aerofs.restless.Configuration;
import com.aerofs.restless.Service;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetSocketAddress;
import java.util.Set;

import static com.aerofs.dryad.DryadProperties.SERVER_HOSTNAME;
import static com.aerofs.dryad.DryadProperties.SERVER_PORT;
import static java.util.concurrent.Executors.newCachedThreadPool;

public class DryadServer extends Service
{
    private static final Logger l = LoggerFactory.getLogger(DryadServer.class);

    private DryadServer(Injector injector, DryadProperties properties)
    {
        super("dryad-upload", getServerAddressFromProperties(properties), injector,
                newCachedThreadPool());

        addResource(LogsResource.class);
        addResource(HealthCheckResource.class);
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

        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable)
            {
                l.error("uncaught exception thd:{} err:{} - kill system",
                        thread.getName(), throwable, throwable);
                System.exit(0x4655434b);
            }
        });

        ProgramBanner.logBanner("banner.txt");

        File propertiesFile = new File(args[0]);
        DryadProperties properties = DryadProperties.loadFromFile(propertiesFile);

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
        return ImmutableSet.of(
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
