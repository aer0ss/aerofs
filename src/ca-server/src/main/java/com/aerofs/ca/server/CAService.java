/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.ca.server;

import com.aerofs.auth.server.SharedSecret;
import com.aerofs.auth.server.shared.AeroService;
import com.aerofs.auth.server.shared.AeroServiceSharedSecretAuthenticator;
import com.aerofs.base.Loggers;
import com.aerofs.baseline.Environment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.baseline.db.Databases;
import com.aerofs.ca.database.CADatabase;
import com.aerofs.ca.server.config.CAConfig;
import com.aerofs.ca.server.resources.CAResource;
import com.aerofs.ca.server.resources.CAInitializedCheck;
import com.aerofs.ca.server.resources.InvalidCSRExceptionMapper;
import com.aerofs.ca.utils.CertificateSigner;
import com.aerofs.ca.utils.CertificateUtils;
import com.aerofs.ca.utils.KeyUtils;
import com.aerofs.lib.configuration.ServerConfigurationSetter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.flywaydb.core.Flyway;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.sql.DataSource;
import javax.ws.rs.core.Context;
import java.security.KeyPair;
import java.security.Security;

public class CAService extends Service<CAConfig>
{
    private static final Logger l = Loggers.getLogger(CAService.class);

    public CAService()
    {
        super("certificate-authority-server");
    }

    public static void main(String args[])
            throws Exception
    {
        Loggers.init();
        l.info("Certificate Authority service started.");

        CAService service = new CAService();
        service.run(args);
    }

    @Override
    public void init(CAConfig configuration, Environment environment)
            throws Exception
    {
        Security.addProvider(new BouncyCastleProvider());

        DatabaseConfiguration database = configuration.getDatabase();
        DataSource dataSource = Databases.newDataSource(database);

        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.migrate(); // IMPORTANT: we use the default schema for the connection

        DBI dbi = Databases.newDBI(dataSource);

        // load the CA key from the database
        CADatabase db = new CADatabase(dbi);
        CertificateSigner certificateSigner;
        if (db.initialized()) {
            l.info("loading key and certificate from database");
            certificateSigner = CertificateSigner.certificateSignerWithKeyAndCert(db.getKeyPair(), db.getCACert());
        } else {
            l.info("no key and certificate found in database, creating new ones");
            KeyPair keys = KeyUtils.newKeyPair();
            certificateSigner = CertificateSigner.certificateSignerWithKeys(keys);
            db.setKeyAndCert(keys.getPrivate(), certificateSigner.caCert());
        }

        // set the ca_cert config property value in the config server (though the canonical copy is found in the database)
        ServerConfigurationSetter.setProperty("ca-server", "base_ca_cert", CertificateUtils.encodeCertForSavingToFile(certificateSigner.caCert()));

        environment.addResource(CAResource.class);
        environment.registerHealthCheck("initialized", CAInitializedCheck.class);

        environment.addServiceProvider(InvalidCSRExceptionMapper.class);

        environment.addAuthenticator(AeroServiceSharedSecretAuthenticator.class);
        environment.addBinder(new AbstractBinder()
        {
            @Override
            protected void configure()
            {
                bindFactory(SharedSecretAuthenticatorFactory.class).to(AeroServiceSharedSecretAuthenticator.class);
                bindFactory(SharedSecretFactory.class).to(SharedSecret.class);
                bind(dbi).to(DBI.class);
                bind(db).to(CADatabase.class);
                bind(certificateSigner).to(CertificateSigner.class);
                bind(CAInitializedCheck.class).to(CAInitializedCheck.class);
            }
        });

        l.info("ca-server initialization finished");
    }
}

class SharedSecretFactory implements Factory<SharedSecret>
{
    @Override
    public SharedSecret provide()
    {
        return new SharedSecret(AeroService.loadDeploymentSecret());
    }

    @Override
    public void dispose(SharedSecret instance)
    {
        // noop
    }
}

class SharedSecretAuthenticatorFactory implements Factory<AeroServiceSharedSecretAuthenticator>
{
    private SharedSecret deploymentSecret;

    @Inject
    public SharedSecretAuthenticatorFactory(@Context SharedSecret deploymentSecret)
    {
        this.deploymentSecret = deploymentSecret;
    }

    @Override
    public AeroServiceSharedSecretAuthenticator provide()
    {
        return new AeroServiceSharedSecretAuthenticator(deploymentSecret);
    }

    @Override
    public void dispose(AeroServiceSharedSecretAuthenticator instance)
    {
        // noop
    }
}
