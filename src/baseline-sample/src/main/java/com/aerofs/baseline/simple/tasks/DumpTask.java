package com.aerofs.baseline.simple.tasks;

import com.aerofs.baseline.admin.Task;
import com.aerofs.baseline.admin.TasksResource;
import com.aerofs.baseline.simple.api.Customer;
import com.aerofs.baseline.simple.db.Customers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import javax.ws.rs.core.MultivaluedMap;
import java.io.PrintWriter;
import java.util.List;

/**
 * Dump the entire list of customers.
 */
public final class DumpTask implements Task {

    private final DBI dbi;
    private final ObjectMapper objectMapper;

    public DumpTask(DBI dbi, ObjectMapper objectMapper) {
        this.dbi = dbi;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "dump";
    }

    @Override
    public void execute(MultivaluedMap<String, String> queryParameters, PrintWriter outputWriter) throws Exception {
        final List<Customer> existingCustomers = Lists.newArrayListWithCapacity(10);

        dbi.inTransaction(new TransactionCallback<Void>() {

            @Override
            public Void inTransaction(Handle conn, TransactionStatus status) throws Exception {
                Customers customers = conn.attach(Customers.class);

                ResultIterator<Customer> iterator = customers.get();
                try {
                    while(iterator.hasNext()) {
                        existingCustomers.add(iterator.next());
                    }
                } finally {
                    iterator.close();
                }

                return null;
            }
        });

        TasksResource.printFormattedJson(existingCustomers, queryParameters, objectMapper, outputWriter);
    }
}
