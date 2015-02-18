package com.aerofs.polaris;

import com.aerofs.auth.server.AeroUserDevicePrincipalBinder;
import com.aerofs.auth.server.cert.AeroDeviceCertAuthenticator;
import com.aerofs.auth.server.cert.AeroDeviceCertPrincipalBinder;
import com.aerofs.baseline.Environment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.db.DBIBinder;
import com.aerofs.baseline.db.DBIExceptionMapper;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.baseline.db.Databases;
import com.aerofs.polaris.api.operation.Operation;
import com.aerofs.polaris.dao.ObjectTypeArgument;
import com.aerofs.polaris.dao.TransformTypeArgument;
import com.aerofs.polaris.logical.LogicalObjectStore;
import com.aerofs.polaris.logical.TreeCommand;
import com.aerofs.polaris.resources.BatchResource;
import com.aerofs.polaris.resources.ObjectsResource;
import com.aerofs.polaris.resources.TransformsResource;
import com.aerofs.polaris.sp.SPAccessManagerBinder;
import org.flywaydb.core.Flyway;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.skife.jdbi.v2.DBI;

import javax.sql.DataSource;

public final class Polaris extends Service<PolarisConfiguration> {

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

        // register the command that dumps the object tree
        environment.registerCommand("tree", TreeCommand.class);

        // setup the object store
        LogicalObjectStore logicalObjectStore = new LogicalObjectStore(dbi);
        environment.addBinder(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(logicalObjectStore).to(LogicalObjectStore.class);
                bind(TreeCommand.class).to(TreeCommand.class);
            }
        });

        // setup resource authorization
        environment.addAuthenticator(new AeroDeviceCertAuthenticator());
        environment.addAdminProvider(new AeroDeviceCertPrincipalBinder());
        environment.addAdminProvider(new AeroUserDevicePrincipalBinder());
        environment.addServiceProvider(new AeroDeviceCertPrincipalBinder());
        environment.addServiceProvider(new AeroUserDevicePrincipalBinder());

        // register singleton providers
        environment.addServiceProvider(new DBIBinder(dbi));
        environment.addServiceProvider(new SPAccessManagerBinder());
        environment.addServiceProvider(DBIExceptionMapper.class);
        environment.addServiceProvider(PolarisExceptionMapper.class);

        // setup the api-object deserializer
        Operation.registerDeserializer(environment.getMapper());

        // register root resources
        environment.addResource(BatchResource.class);
        environment.addResource(ObjectsResource.class);
        environment.addResource(TransformsResource.class);
    }
}
