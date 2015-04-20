/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.auth.server.shared.AeroService;
import com.aerofs.base.BaseParam.Cacert;
import com.aerofs.base.DefaultUncaughtExceptionHandler;
import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.FileBasedCertificateProvider;
import com.aerofs.bifrost.module.AccessTokenRepositoryImpl;
import com.aerofs.bifrost.module.AuthorizationRequestRepositoryImpl;
import com.aerofs.bifrost.module.ClientRepositoryImpl;
import com.aerofs.bifrost.module.ResourceServerRepositoryImpl;
import com.aerofs.bifrost.oaaas.auth.OAuth2Validator;
import com.aerofs.bifrost.oaaas.auth.OAuth2ValidatorImpl;
import com.aerofs.bifrost.oaaas.model.AccessToken;
import com.aerofs.bifrost.oaaas.model.AuthorizationRequest;
import com.aerofs.bifrost.oaaas.model.Client;
import com.aerofs.bifrost.oaaas.model.ResourceServer;
import com.aerofs.bifrost.oaaas.repository.AccessTokenRepository;
import com.aerofs.bifrost.oaaas.repository.AuthorizationRequestRepository;
import com.aerofs.bifrost.oaaas.repository.ClientRepository;
import com.aerofs.bifrost.oaaas.repository.ResourceServerRepository;
import com.aerofs.bifrost.oaaas.resource.AuthorizeResource;
import com.aerofs.bifrost.oaaas.resource.ClientsResource;
import com.aerofs.bifrost.oaaas.resource.HealthCheckResource;
import com.aerofs.bifrost.oaaas.resource.TokenResource;
import com.aerofs.bifrost.oaaas.resource.VerifyResource;
import com.aerofs.lib.configuration.ServerConfigurationLoader;
import com.aerofs.lib.LibParam;
import com.aerofs.rest.auth.DelegatedUserExtractor;
import com.aerofs.rest.auth.SharedSecretExtractor;
import com.aerofs.rest.providers.AuthProvider;
import com.aerofs.restless.Configuration;
import com.aerofs.restless.Service;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.IDataSourceProvider;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.internal.Scoping;
import org.hibernate.SessionFactory;

import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.util.Properties;
import java.util.Set;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;

public class Bifrost extends Service
{
    static
    {
        Loggers.init();
    }

    @Inject
    public Bifrost(Injector injector, String deploymentSecret)
    {
        super("bifrost", new InetSocketAddress(getIntegerProperty("bifrost.port", 8700)), injector);

        addResource(new AuthProvider(
                new SharedSecretExtractor(deploymentSecret),
                new DelegatedUserExtractor(deploymentSecret)
        ));

        addResource(AuthorizeResource.class);
        addResource(VerifyResource.class);
        addResource(TokenResource.class);
        addResource(ClientsResource.class);
        addResource(HealthCheckResource.class);
    }


    @Override
    protected Set<Class<?>> singletons()
    {
        return ImmutableSet.of(TransactionalWrapper.class);
    }

    public static Module spModule()
    {
        return new AbstractModule() {
            private final SPBlockingClient.Factory factSP =
                    new SPBlockingClient.Factory(new FileBasedCertificateProvider(Cacert.FILE));

            @Override
            protected void configure()
            {
                bind(SPBlockingClient.Factory.class).toInstance(factSP);
            }
        };
    }

    public static Module databaseModule(IDataSourceProvider dsProvider)
    {
        return new AbstractModule()
        {
            @Override
            protected void configure()
            {
                BifrostSessionFactory factory = new BifrostSessionFactory(dsProvider);
                bind(SessionFactory.class).toInstance(factory.build(
                        ResourceServer.class,
                        AccessToken.class,
                        Client.class,
                        AuthorizationRequest.class
                ));
            }
        };
    }

    public static Module bifrostModule()
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
            }
        });
    }
}

