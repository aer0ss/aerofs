package com.aerofs.polaris;

import com.aerofs.baseline.AdminEnvironment;
import com.aerofs.baseline.LifecycleManager;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.ServiceEnvironment;
import com.aerofs.baseline.db.DBIBinder;
import com.aerofs.baseline.db.DBIExceptionMapper;
import com.aerofs.baseline.db.DBIInstances;
import com.aerofs.baseline.db.DataSources;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.polaris.api.operation.Operation;
import com.aerofs.polaris.dao.ObjectTypeArgument;
import com.aerofs.polaris.dao.TransformTypeArgument;
import com.aerofs.polaris.logical.LogicalObjectStore;
import com.aerofs.polaris.logical.LogicalObjectStoreBinder;
import com.aerofs.polaris.logical.TreeTask;
import com.aerofs.polaris.resources.BatchResource;
import com.aerofs.polaris.resources.ObjectsResource;
import com.aerofs.polaris.resources.TransformsResource;
import com.aerofs.polaris.sp.SPAccessManagerBinder;
import org.flywaydb.core.Flyway;
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
    public void init(PolarisConfiguration configuration, LifecycleManager lifecycle, AdminEnvironment admin, ServiceEnvironment service) throws Exception {
        // initialize the database connection pool
        DatabaseConfiguration database = configuration.getDatabase();
        DataSource dataSource = DataSources.newManagedDataSource(database, lifecycle);

        // setup the database
        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.migrate(); // IMPORTANT: we use the default schema for the connection

        // setup JDBI
        DBI dbi = DBIInstances.newDBI(dataSource);
        dbi.registerArgumentFactory(new ObjectTypeArgument.ObjectTypeArgumentFactory());
        dbi.registerArgumentFactory(new TransformTypeArgument.TransformTypeArgumentFactory());

        // setup the object store
        LogicalObjectStore logicalObjectStore = new LogicalObjectStore(dbi);

        // register the task that dumps the object tree
        admin.registerTask(new TreeTask(logicalObjectStore, admin.getMapper()));

        // register singleton providers
        service.addProvider(new DBIBinder(dbi));
        service.addProvider(new DBIExceptionMapper());
        service.addProvider(new PolarisExceptionMapper());
        service.addProvider(new LogicalObjectStoreBinder(logicalObjectStore));
        service.addProvider(new SPAccessManagerBinder());

        // setup the api-object deserializer
        Operation.registerDeserializer(service.getMapper());

        // register root resources
        service.addResource(BatchResource.class);
        service.addResource(ObjectsResource.class);
        service.addResource(TransformsResource.class);
    }
}
