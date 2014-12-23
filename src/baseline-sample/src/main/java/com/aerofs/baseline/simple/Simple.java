package com.aerofs.baseline.simple;

import com.aerofs.baseline.AdminEnvironment;
import com.aerofs.baseline.RootEnvironment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.ServiceEnvironment;
import com.aerofs.baseline.db.DBIBinder;
import com.aerofs.baseline.db.DBIExceptionMapper;
import com.aerofs.baseline.db.DBIInstances;
import com.aerofs.baseline.db.DataSources;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.baseline.simple.commands.DumpCommand;
import com.aerofs.baseline.simple.resources.CustomersResource;
import com.aerofs.baseline.simple.resources.InvalidCustomerExceptionMapper;
import org.flywaydb.core.Flyway;
import org.skife.jdbi.v2.DBI;

import javax.sql.DataSource;

public final class Simple extends Service<SimpleConfiguration> {

    public static void main(String[] args) throws Exception {
        Simple simple = new Simple();
        simple.run(args);
    }

    public Simple() {
        super("simple");
    }

    @Override
    public void init(SimpleConfiguration configuration, RootEnvironment root, AdminEnvironment admin, ServiceEnvironment service) throws Exception {
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
