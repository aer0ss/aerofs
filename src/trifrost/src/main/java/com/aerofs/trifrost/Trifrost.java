package com.aerofs.trifrost;

import com.aerofs.auth.server.shared.AeroService;
import com.aerofs.baseline.Environment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.db.DBIExceptionMapper;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.baseline.db.Databases;
import com.aerofs.servlets.lib.AbstractEmailSender;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.trifrost.base.Constants;
import com.aerofs.trifrost.base.*;
import com.aerofs.trifrost.resources.AuthResource;
import com.aerofs.trifrost.resources.DeviceResource;
import com.aerofs.trifrost.resources.InviteResource;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.flywaydb.core.Flyway;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.skife.jdbi.v2.DBI;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Properties;

import static com.aerofs.baseline.db.Databases.newDBI;
import static com.aerofs.lib.configuration.ServerConfigurationLoader.initialize;

public class Trifrost extends Service<TrifrostConfiguration> {
    public static void main(String[] args) throws Exception {
        Trifrost profileServer = new Trifrost("profile");
        profileServer.run(args);
    }

    protected Trifrost(String name) {
        super(name);
    }

    @Override
    public void init(TrifrostConfiguration configuration, Environment environment) throws Exception {

        initConfigProperties();

        // initialize the database connection pool
        DatabaseConfiguration database = configuration.getDatabase();
        DataSource dataSource = Databases.newManagedDataSource(environment, database);

        // setup the database
        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.migrate(); // IMPORTANT: we use the default schema for the connection
        DBI dbi = newDBI(dataSource);

        environment.getMapper().setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);

        String deploymentSecret = getDeploymentSecret(configuration);

        environment.addBinder(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(deploymentSecret).to(String.class).named(Constants.DEPLOYMENT_SECRET_INJECTION_KEY);

                bind(AsyncEmailSender.create()).to(AbstractEmailSender.class);
                bind(UniqueIDGenerator.create()).to(UniqueIDGenerator.class);
                bind(configuration.getUnifiedPush()).to(UnifiedPushConfiguration.class);
                bind(dbi).to(DBI.class);
            }
        });

        // setup authentication
        environment.addAuthenticator(new HttpBasicAuthenticator(dbi));

        environment.addServiceProvider(DBIExceptionMapper.class);
        environment.addServiceProvider(new UserBinder());
        environment.addServiceProvider(new DeviceNotFoundException.Mapper());
        environment.addServiceProvider(new InvalidCodeException.Mapper());
        environment.addServiceProvider(new UserNotFoundException.Mapper());
        environment.addServiceProvider(new UserNotAuthorizedException.Mapper());

        // register root resources
        environment.addResource(AuthResource.class);
        environment.addResource(DeviceResource.class);
        environment.addResource(InviteResource.class);
    }

    protected void initConfigProperties() throws Exception {
        initialize("trifrost", new Properties());
    }
    protected String getDeploymentSecret(TrifrostConfiguration configuration) throws IOException {
        return AeroService.loadDeploymentSecret(configuration.getDeploymentSecretPath());
    }
}
