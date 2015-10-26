/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditorFactory;
import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.base.C;
import com.aerofs.base.DefaultUncaughtExceptionHandler;
import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.base.ssl.URLBasedCertificateProvider;
import com.aerofs.bifrost.oaaas.auth.NonceChecker;
import com.aerofs.bifrost.server.Bifrost;
import com.aerofs.lib.LibParam.REDIS;
import com.aerofs.lib.configuration.ServerConfigurationLoader;
import com.aerofs.oauth.TokenVerifier;
import com.aerofs.rest.auth.DelegatedUserDeviceExtractor;
import com.aerofs.rest.auth.OAuthExtractor;
import com.aerofs.rest.auth.OAuthRequestFilter;
import com.aerofs.rest.auth.SharedSecretExtractor;
import com.aerofs.rest.providers.*;
import com.aerofs.restless.Configuration;
import com.aerofs.restless.Service;
import com.aerofs.restless.Version;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.authentication.Authenticator;
import com.aerofs.sp.authentication.AuthenticatorFactory;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.sparta.providers.CertAuthExtractor;
import com.aerofs.sp.sparta.providers.TransactionWrapper;
import com.aerofs.sp.sparta.providers.WirableMapper;
import com.aerofs.sp.sparta.resources.*;
import com.aerofs.ssmp.SSMPConnection;
import com.google.common.collect.ImmutableSet;
import com.google.inject.*;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

/**
 * Standalone RESTful SP
 */
public class Sparta extends Service
{
    static
    {
        Loggers.init();
    }

    static final Version HIGHEST_SUPPORTED_VERSION = new Version(1, 3);

    private final ExecutionHandler _executionHandler;

    public Sparta(Injector injector, String deploymentSecret)
    {
        super("sparta", listenAddress(), injector);

        _executionHandler = new ExecutionHandler(
                new OrderedMemoryAwareThreadPoolExecutor(10, 1 * C.MB, 5 * C.MB));

        addRequestFilter(OAuthRequestFilter.class);

        enableVersioning();

        addResource(new AuthProvider(
                new OAuthExtractor(),
                new CertAuthExtractor(injector.getInstance(CertificateDatabase.class)),
                new DelegatedUserDeviceExtractor(deploymentSecret),
                new SharedSecretExtractor(deploymentSecret)));

        addResource(InviteesResource.class);
        addResource(UsersResource.class);
        addResource(DevicesResource.class);
        addResource(SharedFolderResource.class);
        addResource(OrganizationsResource.class);
        addResource(GroupResource.class);
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

        Thread.setDefaultUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler());

        ServerConfigurationLoader.initialize("sparta", extra);

        ICertificateProvider cacert = URLBasedCertificateProvider.server();

        Timer timer = new HashedWheelTimer();
        ClientSocketChannelFactory clientFactory = new NioClientSocketChannelFactory();

        String secret = AeroService.loadDeploymentSecret();

        SpartaSQLConnectionProvider sqlConnProvider = new SpartaSQLConnectionProvider();

        Module clients = clientsModule(cacert, secret, timer);

        Injector inj = Guice.createInjector(Stage.PRODUCTION,
                databaseModule(sqlConnProvider),
                clients,
                spartaModule(timer, clientFactory));


        // NB: we expect nginx or similar to provide ssl termination...
        new Sparta(inj, secret)
                .start();

        // And we embed a Bifrost out of whole cloth, too, because why not.
        Injector bifrostInj = Guice.createInjector(Stage.PRODUCTION,
                Bifrost.databaseModule(sqlConnProvider),
                Bifrost.bifrostModule(),
                databaseModule(sqlConnProvider),
                clients,
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(NonceChecker.class).to(InProcessNonceChecker.class);
                    }
                }
        );

        new Bifrost(bifrostInj, secret)
                .start();
    }

    @Override
    public void stop()
    {
        super.stop();
        _executionHandler.releaseExternalResources();
    }

    @Override
    public ChannelPipeline getSpecializedPipeline()
    {
        ChannelPipeline p = super.getSpecializedPipeline();

        // free i/o threads while handling app logic and preserve HTTP pipelining
        p.addBefore(JERSEY_HANLDER, "exec", _executionHandler);
        return p;
    }

    @Override
    protected Set<Class<?>> singletons()
    {
        return ImmutableSet.of(
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

    static private Module clientsModule(final ICertificateProvider cacert, String secret, Timer timer)
    {
        return new AbstractModule() {
            Executor executor = Executors.newCachedThreadPool();
            SSMPConnection c = new SSMPConnection(secret,
                    InetSocketAddress.createUnresolved("lipwig.service", 8787),
                    timer,
                    new NioClientSocketChannelFactory(executor, executor, 1, 2),
                    new SSLEngineFactory(Mode.Client, Platform.Desktop, null, cacert, null)::newSslHandler
            );

            @Override
            protected void configure()
            {
                PooledJedisConnectionProvider jedisConn = new PooledJedisConnectionProvider();
                jedisConn.init_(REDIS.AOF_ADDRESS.getHostName(), REDIS.AOF_ADDRESS.getPort(), REDIS.PASSWORD);

                bind(PooledJedisConnectionProvider.class).toInstance(jedisConn);
                bind(new TypeLiteral<IDatabaseConnectionProvider<JedisPooledConnection>>() {})
                        .toInstance(jedisConn);
                bind(new TypeLiteral<IThreadLocalTransaction<JedisException>>() {})
                        .to(JedisThreadLocalTransaction.class);
            }

            @Provides @Singleton
            public SSMPConnection providesPublisher()
            {
                c.start();
                return c;
            }

            @Provides @Singleton
            public AuditClient providesAudit()
            {
                AuditClient client = new AuditClient();
                client.setAuditorClient(AuditorFactory.createAuthenticatedWithSharedSecret("auditor.service", "sparta", secret));
                return client;
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

            @Provides @Singleton
            public Authenticator providesAuthenticator(AuthenticatorFactory authenticatorFactory)
            {
                return authenticatorFactory.create();
            }
        };
    }
}
