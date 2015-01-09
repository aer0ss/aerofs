package com.aerofs.baseline.sample;

import com.aerofs.baseline.AdminEnvironment;
import com.aerofs.baseline.RootEnvironment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.ServiceEnvironment;
import com.aerofs.baseline.db.DBIBinder;
import com.aerofs.baseline.db.DBIExceptionMapper;
import com.aerofs.baseline.db.DBIInstances;
import com.aerofs.baseline.db.DataSources;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.baseline.sample.commands.DumpCommand;
import com.aerofs.baseline.sample.resources.CustomersResource;
import com.aerofs.baseline.sample.resources.InvalidCustomerExceptionMapper;
import org.flywaydb.core.Flyway;
import org.skife.jdbi.v2.DBI;

import javax.sql.DataSource;

public final class Sample extends Service<SampleConfiguration> {

    public static void main(String[] args) throws Exception {
        Sample sample = new Sample();
        sample.run(args);
    }

    public Sample() {
        super("sample");
    }

    @Override
    public void init(SampleConfiguration configuration, RootEnvironment root, AdminEnvironment admin, ServiceEnvironment service) throws Exception {
        // initialize the database connection pool
        DatabaseConfiguration database = configuration.getDatabase();
        DataSource dataSource = DataSources.newManagedDataSource(root, database);

        // setup the database
        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.migrate(); // IMPORTANT: we use the default schema for the connection

        // setup JDBI
        DBI dbi = DBIInstances.newDBI(dataSource);

        // setup the injector for both the admin and service environments
        root.addInjectable(new DBIBinder(dbi));

        // register the task that dumps the list of customers
        admin.registerCommand("dump", DumpCommand.class);

        // register singleton providers
        service.addProvider(DBIExceptionMapper.class);
        service.addProvider(InvalidCustomerExceptionMapper.class);

        // register root resources
        service.addResource(CustomersResource.class);
    }
}
