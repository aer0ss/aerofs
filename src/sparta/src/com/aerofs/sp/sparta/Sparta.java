/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta;

import com.aerofs.base.BaseParam.Cacert;
import com.aerofs.base.BaseParam.Verkehr;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.Version;
import com.aerofs.base.ssl.FileBasedCertificateProvider;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.lib.properties.Configuration.Server;
import com.aerofs.oauth.TokenVerifier;
import com.aerofs.rest.providers.IllegalArgumentExceptionMapper;
import com.aerofs.rest.providers.JsonExceptionMapper;
import com.aerofs.rest.providers.ParamExceptionMapper;
import com.aerofs.rest.providers.RuntimeExceptionMapper;
import com.aerofs.restless.Configuration;
import com.aerofs.restless.Service;
import com.aerofs.servlets.lib.NoopConnectionListener;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.sp.sparta.providers.AuthProvider;
import com.aerofs.sp.sparta.resources.DevicesResource;
import com.aerofs.sp.sparta.resources.SharesResource;
import com.aerofs.sp.sparta.resources.UsersResource;
import com.aerofs.verkehr.client.lib.publisher.ClientFactory;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.Scoping;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.sql.Connection;
import java.util.Properties;
import java.util.Set;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static java.util.concurrent.Executors.newCachedThreadPool;

/**
 * Standalone RESTful SP
 */
public class Sparta extends Service
{
    static
    {
        Loggers.init();
    }

    static final Version HIGHEST_SUPPORTED_VERSION = new Version(1, 1);

    public Sparta(Injector injector, IPrivateKeyProvider kmgr)
    {
        // use a cached thread pool to free-up I/O threads while the requests do db work
        super("sparta", listenAddress(), kmgr, injector, newCachedThreadPool());

        enableVersioning();

        addResource(UsersResource.class);
        addResource(DevicesResource.class);
        addResource(SharesResource.class);
    }

    private static InetSocketAddress listenAddress()
    {
        return new InetSocketAddress(getStringProperty("sparta.host", "localhost"),
                getIntegerProperty("sparta.port", 8085));
    }

    public static void main(String[] args) throws Exception
    {
        Properties extra = new Properties();
        if (args.length > 0) extra.load(new FileInputStream(args[0]));

        Server.initialize(extra);
        ICertificateProvider cacert = new FileBasedCertificateProvider(Cacert.FILE);

        Timer timer = new HashedWheelTimer();
        ClientSocketChannelFactory clientFactory = new NioClientSocketChannelFactory();

        Injector inj = Guice.createInjector(databaseModule(),
                verkehrModule(cacert, timer, clientFactory),
                spartaModule(timer, clientFactory));

        // NB: we expect nginx or similar to provide ssl termination...
        new Sparta(inj, null)
                .start();
    }

    @Override
    protected Set<Class<?>> singletons()
    {
        return ImmutableSet.of(
                AuthProvider.class,
                JsonExceptionMapper.class,
                ParamExceptionMapper.class,
                IllegalArgumentExceptionMapper.class,
                RuntimeExceptionMapper.class
        );
    }

    static private Module databaseModule()
    {
        return new AbstractModule() {
            @Override
            protected void configure()
            {
                bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);
                bind(new TypeLiteral<IDatabaseConnectionProvider<Connection>>() {})
                        .toInstance(new SpartaSQLConnectionProvider());
            }
        };
    }

    static private Module verkehrModule(final ICertificateProvider cacert, final Timer timer,
            final ClientSocketChannelFactory clientFactory)
    {
        return new AbstractModule() {
            private VerkehrPublisher publisher;
            private final ClientFactory factClient = new ClientFactory(
                    Verkehr.HOST,
                    Short.parseShort(Verkehr.PUBLISH_PORT),
                    clientFactory,
                    cacert,
                    5 * C.SEC,
                    1 * C.SEC,
                    timer,
                    new NoopConnectionListener(),
                    sameThreadExecutor());

            @Override
            protected void configure()
            {}

            @Provides
            public VerkehrPublisher providesPublisher()
            {
                if (publisher == null) publisher = factClient.create();
                return publisher;
            }
        };
    }

    static public Module spartaModule(final Timer timer, final ClientSocketChannelFactory clientFactory)
    {
        return (new AbstractModule() {
            private TokenVerifier verifier;

            @Override
            protected void configure()
            {
                bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);
                bind(Configuration.class).to(SpartaConfiguration.class);
            }

            @Provides
            public TokenVerifier providesVerifier()
            {
                if (verifier == null) {
                    verifier = new TokenVerifier(
                            getStringProperty("sparta.oauth.id", ""),
                            getStringProperty("sparta.oauth.secret", ""),
                            URI.create(getStringProperty("sparta.oauth.url",
                                    "https://localhost:8700/tokeninfo")),
                            timer,
                            null,
                            clientFactory
                    );
                }
                return verifier;
            }
        });
    }
}
