/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.base.ex.ExNotFound;
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
import com.aerofs.rest.auth.AuthTokenExtractor;
import com.aerofs.rest.auth.DelegatedUserExtractor;
import com.aerofs.rest.auth.SharedSecretExtractor;
import com.aerofs.rest.providers.AuthProvider;
import com.aerofs.restless.Configuration;
import com.aerofs.restless.Service;
import com.aerofs.servlets.lib.db.sql.IDataSourceProvider;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.CertAuthExtractor;
import com.aerofs.sp.CertAuthExtractor.CertificateRevocationChecker;
import com.aerofs.sp.CertAuthToken;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.google.common.collect.ImmutableSet;
import com.google.inject.*;
import com.google.inject.internal.Scoping;
import org.hibernate.SessionFactory;

import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.Set;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;

public class Bifrost extends Service
{
    @Inject
    public Bifrost(Injector injector, String deploymentSecret)
    {
        super("bifrost", new InetSocketAddress(getIntegerProperty("bifrost.port", 8700)), injector);

        AuthTokenExtractor<CertAuthToken> certExtractor = null;
        try {
            // FIXME: burn hibernate in favor of libservlet-style explicit SQL
            // this would allow unified transaction and avoid this weird mixed trans business
            certExtractor = new CertAuthExtractor(new CertificateRevocationChecker() {
                private final SQLThreadLocalTransaction trans
                        = injector.getInstance(SQLThreadLocalTransaction.class);
                private final CertificateDatabase certdb
                        = injector.getInstance(CertificateDatabase.class);
                @Override
                public boolean isRevoked(long serial) throws ExNotFound, SQLException {
                    l.debug("check revocation {}", serial);
                    trans.begin();
                    try {
                        return certdb.isRevoked(serial);
                    } finally {
                        trans.rollback();
                    }
                }
            });
            l.info("cert auth");
        } catch (ConfigurationException e) {
            l.info("no cert auth");
        }

        addResource(new AuthProvider(
                new SharedSecretExtractor(deploymentSecret),
                new DelegatedUserExtractor(deploymentSecret),
                certExtractor
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

