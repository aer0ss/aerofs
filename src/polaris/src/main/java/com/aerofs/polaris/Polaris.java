package com.aerofs.polaris;

import com.aerofs.auth.server.AeroUserDevicePrincipalBinder;
import com.aerofs.auth.server.cert.AeroDeviceCertAuthenticator;
import com.aerofs.auth.server.cert.AeroDeviceCertPrincipalBinder;
import com.aerofs.auth.server.cert.AeroOAuthAuthenticator;
import com.aerofs.auth.server.cert.AeroOAuthPrincipalBinder;
import com.aerofs.auth.server.shared.AeroService;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.URLBasedCertificateProvider;
import com.aerofs.baseline.ConstraintViolationExceptionMapper;
import com.aerofs.baseline.DefaultExceptionMapper;
import com.aerofs.baseline.Environment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.auth.AuthenticationExceptionMapper;
import com.aerofs.baseline.db.DBIExceptionMapper;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.baseline.db.Databases;
import com.aerofs.baseline.json.JsonProcessingExceptionMapper;
import com.aerofs.oauth.TokenVerifier;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.acl.ManagedAccessManager;
import com.aerofs.polaris.api.PolarisModule;
import com.aerofs.polaris.dao.types.*;
import com.aerofs.polaris.external_api.exception_providers.ParamExceptionMapper;
import com.aerofs.polaris.external_api.metadata.MetadataBuilder;
import com.aerofs.polaris.external_api.version.VersionFilterDynamicFeature;
import com.aerofs.polaris.external_api.version.VersionProvider;
import com.aerofs.polaris.logical.IllegalArgumentExceptionMapper;
import com.aerofs.polaris.logical.Migrator;
import com.aerofs.polaris.logical.ObjectStore;
import com.aerofs.polaris.logical.TreeCommand;
import com.aerofs.polaris.notification.*;
import com.aerofs.polaris.resources.BatchResource;
import com.aerofs.polaris.resources.JobsResource;
import com.aerofs.polaris.resources.ObjectsResource;
import com.aerofs.polaris.resources.TransformsResource;
import com.aerofs.polaris.resources.external_api.ChildrenResource;
import com.aerofs.polaris.resources.external_api.FilesResource;
import com.aerofs.polaris.resources.external_api.FoldersResource;
import com.aerofs.polaris.sparta.SpartaAccessManager;
import com.aerofs.polaris.sparta.SpartaConfiguration;
import com.aerofs.polaris.ssmp.SSMPPublisher;
import com.aerofs.rest.util.MimeTypeDetector;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.flywaydb.core.Flyway;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.skife.jdbi.v2.DBI;

import javax.inject.Singleton;
import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;

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
        dbi.registerArgumentFactory(new UniqueIDTypeArgument.UniqueIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new OIDTypeArgument.OIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new SIDTypeArgument.SIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new DIDTypeArgument.DIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new ObjectTypeArgument.ObjectTypeArgumentFactory());
        dbi.registerArgumentFactory(new TransformTypeArgument.TransformTypeArgumentFactory());
        dbi.registerArgumentFactory(new JobStatusArgument.JobStatusArgumentFactory());
        dbi.registerArgumentFactory(new LockStatusArgument.LockStatusArgumentFactory());
        dbi.registerMapper(new OneColumnUniqueIDMapper());
        dbi.registerMapper(new OneColumnDIDMapper());
        dbi.registerMapper(new OneColumnOIDMapper());
        dbi.registerMapper(new OneColumnSIDMapper());

        // setup polaris json api conversion
        environment.getMapper().registerModule(new PolarisModule());

        // pick up the deployment secret
        String deploymentSecret = getDeploymentSecret(configuration);

        // fetch cacert
        ICertificateProvider cacert = URLBasedCertificateProvider.server();

        // register the command that dumps the object tree
        environment.registerCommand("tree", TreeCommand.class);

        // add domain objects to the root injector
        environment.addBinder(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(dbi).to(DBI.class);
                bind(configuration.getSparta()).to(SpartaConfiguration.class);
                bind(cacert).to(ICertificateProvider.class);
                bind(deploymentSecret).to(String.class).named(Constants.DEPLOYMENT_SECRET_INJECTION_KEY);
                bind(OrderedNotifier.class).to(ManagedNotifier.class).to(Notifier.class).in(Singleton.class);
                bind(SSMPPublisher.class).to(ManagedUpdatePublisher.class).to(UpdatePublisher.class).in(Singleton.class);
                bind(SpartaAccessManager.class).to(ManagedAccessManager.class).to(AccessManager.class).in(Singleton.class);
                bind(Migrator.class).to(Migrator.class).in(Singleton.class);
                bind(ObjectStore.class).to(ObjectStore.class).in(Singleton.class);
                bind(TreeCommand.class).to(TreeCommand.class);
                bind(MimeTypeDetector.class).to(MimeTypeDetector.class).in(Singleton.class);
                bind(MetadataBuilder.class).to(MetadataBuilder.class).in(Singleton.class);
            }
        });

        environment.addManaged(ManagedAccessManager.class);
        environment.addManaged(ManagedUpdatePublisher.class);
        environment.addManaged(ManagedNotifier.class);
        environment.addManaged(Migrator.class);

        // setup resource authorization
        environment.addAuthenticator(new AeroOAuthAuthenticator(tokenVerifier()));
        environment.addAuthenticator(new AeroDeviceCertAuthenticator());
        environment.addAdminProvider(new AeroDeviceCertPrincipalBinder());
        environment.addAdminProvider(new AeroOAuthPrincipalBinder());
        environment.addAdminProvider(new AeroUserDevicePrincipalBinder());
        environment.addServiceProvider(new AeroDeviceCertPrincipalBinder());
        environment.addServiceProvider(new AeroUserDevicePrincipalBinder());
        environment.addServiceProvider(new AeroOAuthPrincipalBinder());

        // register singleton providers
        environment.addAdminProvider(DBIExceptionMapper.class);
        environment.addAdminProvider(PolarisExceptionMapper.class);

        environment.addServiceProvider(DefaultExceptionMapper.class);
        environment.addServiceProvider(AuthenticationExceptionMapper.class);
        environment.addServiceProvider(IllegalArgumentExceptionMapper.class);
        environment.addServiceProvider(JsonProcessingExceptionMapper.class);
        environment.addServiceProvider(ConstraintViolationExceptionMapper.class);

        environment.addServiceProvider(DBIExceptionMapper.class);
        environment.addServiceProvider(PolarisExceptionMapper.class);
        environment.addServiceProvider(ParamExceptionMapper.class);

        environment.addServiceProvider(VersionFilterDynamicFeature.class);
        environment.addServiceProvider(new VersionProvider());

        // register root resources
        environment.addResource(BatchResource.class);
        environment.addResource(ObjectsResource.class);
        environment.addResource(TransformsResource.class);
        environment.addResource(JobsResource.class);
        // register public facing resources.
        environment.addResource(FoldersResource.class);
        environment.addResource(FilesResource.class);
        environment.addResource(ChildrenResource.class);

        environment.getMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    protected String getDeploymentSecret(PolarisConfiguration configuration) throws IOException {
        return AeroService.loadDeploymentSecret(configuration.getDeploymentSecretPath());
    }

    protected TokenVerifier tokenVerifier()
    {
        return new TokenVerifier("oauth-havre",
                "i-am-not-a-restful-secret",
                URI.create("http://sparta.service:8700/tokeninfo"),
                new HashedWheelTimer(),
                null,
                new NioClientSocketChannelFactory());
    }
}
