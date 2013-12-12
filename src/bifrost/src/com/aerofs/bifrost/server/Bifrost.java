/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.bifrost.common.DefaultUncaughtExceptionHandler;
import com.aerofs.bifrost.login.FormAuthenticator;
import com.aerofs.bifrost.module.AccessTokenRepositoryImpl;
import com.aerofs.bifrost.module.AuthorizationRequestRepositoryImpl;
import com.aerofs.bifrost.module.ClientRepositoryImpl;
import com.aerofs.bifrost.module.ResourceServerRepositoryImpl;
import com.aerofs.bifrost.oaaas.auth.AbstractAuthenticator;
import com.aerofs.bifrost.oaaas.auth.AbstractUserConsentHandler;
import com.aerofs.bifrost.oaaas.auth.AuthenticationFilter;
import com.aerofs.bifrost.oaaas.auth.OAuth2Validator;
import com.aerofs.bifrost.oaaas.auth.OAuth2ValidatorImpl;
import com.aerofs.bifrost.oaaas.auth.UserConsentFilter;
import com.aerofs.bifrost.oaaas.model.AccessToken;
import com.aerofs.bifrost.oaaas.model.AuthorizationRequest;
import com.aerofs.bifrost.oaaas.model.Client;
import com.aerofs.bifrost.oaaas.model.ResourceServer;
import com.aerofs.bifrost.oaaas.noop.NoopUserConsentHandler;
import com.aerofs.bifrost.oaaas.repository.AccessTokenRepository;
import com.aerofs.bifrost.oaaas.repository.AuthorizationRequestRepository;
import com.aerofs.bifrost.oaaas.repository.ClientRepository;
import com.aerofs.bifrost.oaaas.repository.ResourceServerRepository;
import com.aerofs.bifrost.oaaas.resource.ClientsResource;
import com.aerofs.bifrost.oaaas.resource.TokenResource;
import com.aerofs.bifrost.oaaas.resource.VerifyResource;
import com.aerofs.lib.properties.Configuration.Server;
import com.aerofs.restless.Configuration;
import com.aerofs.restless.Service;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.internal.Scoping;
import org.hibernate.SessionFactory;
import org.jboss.netty.channel.ChannelPipeline;

import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.Set;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

public class Bifrost extends Service
{
    static
    {
        Thread.setDefaultUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler());
        Loggers.init();
    }

    private final TransactionalWrapper _trans;

    @Inject
    public Bifrost(Injector injector, IPrivateKeyProvider kmgr)
    {
        super("bifrost", new InetSocketAddress(getIntegerProperty("bifrost.port", 8700)), kmgr,
                injector);

        SessionFactory sessionFactory = injector.getInstance(SessionFactory.class);
        _trans = new TransactionalWrapper(sessionFactory,
                ImmutableSet.of("/token", "/authorize", "/clients"),    // read-write
                ImmutableSet.of("/tokeninfo", "/tokenlist"));           // read-only

        addRequestFilter(AuthenticationFilter.class);
        addRequestFilter(UserConsentFilter.class);
    }

    @Override
    protected Set<Class<?>> singletons()
    {
        return ImmutableSet.of(
                VerifyResource.class,
                TokenResource.class,
                ClientsResource.class
        );
    }

    @Override
    public ChannelPipeline getSpecializedPipeline()
    {
        ChannelPipeline p = super.getSpecializedPipeline();
        p.addBefore("jersey", "trans", _trans);
        return p;
    }

    public static void main(String[] args) throws Exception
    {
        Properties extra = new Properties();
        if (args.length > 0) extra.load(new FileInputStream(args[0]));

        Server.initialize(extra);

        Class.forName(getStringProperty("bifrost.db.driverClass", "com.mysql.jdbc.Driver"));

        // Note, we expect nginx or similar to provide ssl termination...
        new Bifrost(Guice.createInjector(databaseModule(), bifrostModule()), null)
                .start();
    }

    static private Module databaseModule()
    {
        return new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind(SessionFactory.class).toInstance(BifrostSessionFactory.build(
                        ResourceServer.class,
                        AccessToken.class,
                        Client.class,
                        AuthorizationRequest.class
                ));
            }
        };
    }

    static public Module bifrostModule()
    {
        return (new AbstractModule() {
            @Override
            protected void configure()
            {
                bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

                bind(Configuration.class).to(BifrostConfiguration.class);

                // database-integration facades:
                //
                bind(AccessTokenRepository.class).to(AccessTokenRepositoryImpl.class);
                bind(AuthorizationRequestRepository.class).to(
                        AuthorizationRequestRepositoryImpl.class);
                bind(ClientRepository.class).to(ClientRepositoryImpl.class);
                bind(ResourceServerRepository.class).to(ResourceServerRepositoryImpl.class);

                // OAuth validators and authenticators
                //
                bind(OAuth2Validator.class).to(OAuth2ValidatorImpl.class);
                bind(AbstractAuthenticator.class).to(FormAuthenticator.class);
                bind(AbstractUserConsentHandler.class).to(NoopUserConsentHandler.class);
            }
        });
    }
}

