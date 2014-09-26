package com.aerofs.polaris;

import com.aerofs.baseline.Environment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.db.DBIExceptionMapper;
import com.aerofs.baseline.db.DBIInstances;
import com.aerofs.baseline.db.DataSources;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.polaris.api.operation.Operation;
import com.aerofs.polaris.dao.ObjectTypeArgument;
import com.aerofs.polaris.dao.TransformTypeArgument;
import com.aerofs.polaris.dao.Transforms;
import com.aerofs.polaris.logical.LogicalObjectStore;
import com.aerofs.polaris.logical.LogicalObjectStoreBinder;
import com.aerofs.polaris.logical.LogicalObjectStoreDumpTask;
import com.aerofs.polaris.resources.BatchResource;
import com.aerofs.polaris.resources.ObjectsResource;
import com.aerofs.polaris.resources.TransformsResource;
import org.flywaydb.core.Flyway;
import org.skife.jdbi.v2.DBI;

import javax.sql.DataSource;

public final class Polaris extends Service<PolarisConfiguration> {

    public static void main(String[] args) throws Exception {
        Polaris polaris = new Polaris();
        polaris.run(args);
    }

    @Override
    public void init(PolarisConfiguration configuration, Environment environment) throws Exception {
        // initialize the database connection pool
        DatabaseConfiguration database = configuration.getDatabase();
        DataSource dataSource = DataSources.newManagedDataSource(database, environment);

        // setup the database
        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.setSchemas("polaris");
        flyway.setInitOnMigrate(true);
        flyway.migrate();

        // setup JDBI
        DBI dbi = DBIInstances.newDBI(dataSource);
        dbi.registerArgumentFactory(new ObjectTypeArgument.ObjectTypeArgumentFactory());
        dbi.registerArgumentFactory(new TransformTypeArgument.TransformTypeArgumentFactory());

        // setup the api-object deserializer
        Operation.registerDeserializer(environment.getObjectMapper());

        // setup the object store
        LogicalObjectStore logicalObjectStore = new LogicalObjectStore(dbi);

        // register the tree-printer
        registerTask(new LogicalObjectStoreDumpTask(logicalObjectStore, environment.getObjectMapper()));

        // setup providers (these are singletons)
        addProvider(new DBIExceptionMapper());
        addProvider(new PolarisExceptionMapper());
        addProvider(new IllegalArgumentExceptionMapper());
        addProvider(new LogicalObjectStoreBinder(logicalObjectStore));

        // setup root resources (these are managed by the container)
        addResource(BatchResource.class);
        addResource(ObjectsResource.class);
        addResource(TransformsResource.class);
    }
}
