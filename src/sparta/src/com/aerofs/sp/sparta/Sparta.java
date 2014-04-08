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
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.sp.authentication.Authenticator;
import com.aerofs.sp.authentication.AuthenticatorFactory;
import com.aerofs.sp.sparta.providers.AuthProvider;
import com.aerofs.sp.sparta.providers.FactoryReaderProvider;
import com.aerofs.sp.sparta.providers.TransactionWrapper;
import com.aerofs.sp.sparta.providers.WirableMapper;
import com.aerofs.sp.sparta.resources.DevicesResource;
import com.aerofs.sp.sparta.resources.SharedFolderResource;
import com.aerofs.sp.sparta.resources.UsersResource;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.aerofs.verkehr.client.lib.publisher.ClientFactory;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.Scoping;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import redis.clients.jedis.JedisPooledConnection;
import redis.clients.jedis.exceptions.JedisException;

import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.sql.Connection;
import java.util.Properties;
import java.util.Set;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_ACK_TIMEOUT;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_RECONNECT_DELAY;
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
        super("sparta", listenAddress(), kmgr, injector, null);

        enableVersioning();

        addResource(UsersResource.class);
        addResource(DevicesResource.class);
        addResource(SharedFolderResource.class);
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

        Injector inj = Guice.createInjector(Stage.PRODUCTION,
                databaseModule(new SpartaSQLConnectionProvider()),
                clientsModule(cacert, timer, clientFactory),
                spartaModule(timer, clientFactory));


        // NB: we expect nginx or similar to provide ssl termination...
        new Sparta(inj, null)
                .start();
    }

    @Override
    public ChannelPipeline getSpecializedPipeline()
    {
        ChannelPipeline p = super.getSpecializedPipeline();

        // free i/o threads while handling app logic and preserve HTTP pipelining
        p.addBefore(JERSEY_HANLDER, "exec", new ExecutionHandler(
                new OrderedMemoryAwareThreadPoolExecutor(10, 1 * C.MB, 5 * C.MB)));
        return p;
    }

    @Override
    protected Set<Class<?>> singletons()
    {
        return ImmutableSet.<Class<?>>of(
                AuthProvider.class,
                FactoryReaderProvider.class,
                TransactionWrapper.class,
                WirableMapper.class,
                JsonExceptionMapper.class,
                ParamExceptionMapper.class,
                IllegalArgumentExceptionMapper.class,
                RuntimeExceptionMapper.class
        );
    }

    public static Module databaseModule(final IDatabaseConnectionProvider<Connection> db)
    {
        return new AbstractModule() {
            @Override
            protected void configure()
            {
                bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);
                /**
                 * SQLThreadLocalTransaction implements IDatabaseConnectionProvider and wraps an
                 * underlying IDatabaseConnectionProvider which makes injection a little convoluted
                 * especially because you want a single instance of it to be injected even though
                 * it can be injected both directly and indirectly by referring to the interface...
                 */
                SQLThreadLocalTransaction t = new SQLThreadLocalTransaction(db);
                bind(SQLThreadLocalTransaction.class).toInstance(t);
                bind(new TypeLiteral<IDatabaseConnectionProvider<Connection>>() {}).toInstance(t);
            }
        };
    }

    static private Module clientsModule(final ICertificateProvider cacert, final Timer timer,
            final ClientSocketChannelFactory clientFactory)
    {
        return new AbstractModule()
        {
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

            private final com.aerofs.verkehr.client.lib.admin.ClientFactory factAdmin =
                    new com.aerofs.verkehr.client.lib.admin.ClientFactory(
                            Verkehr.HOST, Short.parseShort(Verkehr.ADMIN_PORT),
                            newCachedThreadPool(), newCachedThreadPool(),
                            cacert,
                            VERKEHR_RECONNECT_DELAY, VERKEHR_ACK_TIMEOUT, timer,
                            new NoopConnectionListener(), sameThreadExecutor());

            @Override
            protected void configure()
            {
                bind(new TypeLiteral<IDatabaseConnectionProvider<JedisPooledConnection>>() {})
                        .toInstance(new PooledJedisConnectionProvider());
                bind(new TypeLiteral<IThreadLocalTransaction<JedisException>>() {})
                        .to(JedisThreadLocalTransaction.class);
            }

            @Provides @Singleton
            public VerkehrPublisher providesPublisher()
            {
                VerkehrPublisher publisher = factClient.create();
                publisher.start();
                return publisher;
            }

            @Provides @Singleton
            public VerkehrAdmin providesAdmin()
            {
                VerkehrAdmin admin = factAdmin.create();
                admin.start();
                return admin;
            }
        };
    }

    static public Module spartaModule(final Timer timer, final ClientSocketChannelFactory clientFactory)
    {
        return new AbstractModule() {
            @Override
            protected void configure()
            {
                bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);
                bind(Configuration.class).to(SpartaConfiguration.class);
                bind(Timer.class).toInstance(timer);
                bind(Authenticator.class).toInstance(AuthenticatorFactory.create());
                bind(TokenVerifier.class).toInstance(new TokenVerifier(
                        getStringProperty("sparta.oauth.id", ""),
                        getStringProperty("sparta.oauth.secret", ""),
                        URI.create(getStringProperty("sparta.oauth.url",
                                "https://localhost:8700/tokeninfo")),
                        timer,
                        null,
                        clientFactory
                ));
            }
        };
    }
}
