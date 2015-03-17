/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.ca.server;

import com.aerofs.auth.server.SharedSecret;
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
import com.google.common.io.ByteStreams;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.flywaydb.core.Flyway;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.sql.DataSource;
import javax.ws.rs.core.Context;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.Security;

import static com.google.common.base.Preconditions.checkState;

public class CAService extends Service<CAConfig>
{
    private static final Logger l = Loggers.getLogger(CAService.class);
    private static final String CA_CERT_DIR = "/opt/ca/prod";

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

        // save the cert as a PEM to disk, needed by some other services that shouldn't depend on ca-server to be running
        try {
            Files.createDirectories(Paths.get(CA_CERT_DIR));
            Files.write(Paths.get(CA_CERT_DIR, "cacert.pem"), CertificateUtils.x509ToPEM(certificateSigner.caCert()));
        } catch (FileSystemException e) {
            l.warn("could not write cacert file to disk", e);
        }

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
        try (InputStream is = new FileInputStream("/data/deployment_secret")) {
            String s = new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8).trim();
            checkState(s.length() == 32, "Invalid deployment secret %s", s);
            return new SharedSecret(s);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load deployment secret", e);
        }
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
