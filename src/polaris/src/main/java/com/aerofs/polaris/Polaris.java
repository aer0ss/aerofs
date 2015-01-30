package com.aerofs.polaris;

import com.aerofs.auth.server.AeroUserDevicePrincipalBinder;
import com.aerofs.auth.server.cert.AeroDeviceCertAuthenticator;
import com.aerofs.auth.server.cert.AeroDeviceCertPrincipalBinder;
import com.aerofs.baseline.Environment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.db.DBIExceptionMapper;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.baseline.db.Databases;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.operation.Operation;
import com.aerofs.polaris.dao.ObjectTypeArgument;
import com.aerofs.polaris.dao.TransformTypeArgument;
import com.aerofs.polaris.logical.ObjectStore;
import com.aerofs.polaris.logical.TreeCommand;
import com.aerofs.polaris.notification.UpdatePublisher;
import com.aerofs.polaris.resources.BatchResource;
import com.aerofs.polaris.resources.ObjectsResource;
import com.aerofs.polaris.resources.TransformsResource;
import com.aerofs.polaris.sparta.ManagedAccessManager;
import com.aerofs.polaris.sparta.SpartaAccessManager;
import com.aerofs.polaris.sparta.SpartaConfiguration;
import com.aerofs.polaris.verkehr.ManagedUpdatePublisher;
import com.aerofs.polaris.verkehr.ServiceSharedSecretProvider;
import com.aerofs.polaris.verkehr.VerkehrConfiguration;
import com.aerofs.polaris.verkehr.VerkehrPublisher;
import com.aerofs.verkehr.client.rest.AuthorizationHeaderProvider;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.flywaydb.core.Flyway;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.skife.jdbi.v2.DBI;

import javax.inject.Singleton;
import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;

// explicitly not final so that I can override the injected components in the tests
public class Polaris extends Service<PolarisConfiguration> {

    public static void main(String[] args) throws Exception {
        Polaris polaris = new Polaris();
        polaris.run(args);
    }

    public Polaris() {
        super("polaris");
    }

    @Override
    public void init(PolarisConfiguration configuration, Environment environment) throws Exception {
        // initialize the database connection pool
        DatabaseConfiguration database = configuration.getDatabase();
        DataSource dataSource = Databases.newManagedDataSource(environment, database);

        // setup the database
        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.migrate(); // IMPORTANT: we use the default schema for the connection

        // setup JDBI
        DBI dbi = Databases.newDBI(dataSource);
        dbi.registerArgumentFactory(new ObjectTypeArgument.ObjectTypeArgumentFactory());
        dbi.registerArgumentFactory(new TransformTypeArgument.TransformTypeArgumentFactory());

        // setup the api-object deserializer
        Operation.registerDeserializer(environment.getMapper());

        // pick up the deployment secret
        String deploymentSecret = getDeploymentSecret(configuration);

        // register the command that dumps the object tree
        environment.registerCommand("tree", TreeCommand.class);

        // add domain objects to the root injector
        environment.addBinder(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(dbi).to(DBI.class);
                bind(configuration.getVerkehr()).to(VerkehrConfiguration.class);
                bind(configuration.getSparta()).to(SpartaConfiguration.class);
                bind(deploymentSecret).to(String.class).named(Constants.DEPLOYMENT_SECRET_INJECTION_KEY);
                bind(ServiceSharedSecretProvider.class).to(AuthorizationHeaderProvider.class).in(Singleton.class);
                bind(VerkehrPublisher.class).to(ManagedUpdatePublisher.class).to(UpdatePublisher.class).in(Singleton.class);
                bind(SpartaAccessManager.class).to(ManagedAccessManager.class).to(AccessManager.class).in(Singleton.class);
                bind(ObjectStore.class).to(ObjectStore.class).in(Singleton.class);
                bind(TreeCommand.class).to(TreeCommand.class);
            }
        });

        environment.addManaged(ManagedAccessManager.class);
        environment.addManaged(ManagedUpdatePublisher.class);

        // setup resource authorization
        environment.addAuthenticator(new AeroDeviceCertAuthenticator());
        environment.addAdminProvider(new AeroDeviceCertPrincipalBinder());
        environment.addAdminProvider(new AeroUserDevicePrincipalBinder());
        environment.addServiceProvider(new AeroDeviceCertPrincipalBinder());
        environment.addServiceProvider(new AeroUserDevicePrincipalBinder());

        // register singleton providers
        environment.addAdminProvider(DBIExceptionMapper.class);
        environment.addAdminProvider(PolarisExceptionMapper.class);
        environment.addServiceProvider(DBIExceptionMapper.class);
        environment.addServiceProvider(PolarisExceptionMapper.class);

        // register root resources
        environment.addResource(BatchResource.class);
        environment.addResource(ObjectsResource.class);
        environment.addResource(TransformsResource.class);
    }

    protected String getDeploymentSecret(PolarisConfiguration configuration) throws IOException {
        return Files.readFirstLine(new File(configuration.getDeploymentSecretPath()), Charsets.UTF_8);
    }
}
