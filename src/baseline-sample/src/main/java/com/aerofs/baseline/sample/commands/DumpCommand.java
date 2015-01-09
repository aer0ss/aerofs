package com.aerofs.baseline.sample.commands;

import com.aerofs.baseline.admin.Command;
import com.aerofs.baseline.admin.Commands;
import com.aerofs.baseline.sample.api.Customer;
import com.aerofs.baseline.sample.db.Customers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.ResultIterator;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MultivaluedMap;
import java.io.PrintWriter;
import java.util.List;

/**
 * Dump the entire list of customers.
 */
@Singleton
public final class DumpCommand implements Command {

    private final DBI dbi;
    private final ObjectMapper objectMapper;

    @Inject
    public DumpCommand(DBI dbi, ObjectMapper objectMapper) {
        this.dbi = dbi;
        this.objectMapper = objectMapper;
    }

    @Override
    public void execute(MultivaluedMap<String, String> queryParameters, PrintWriter entityWriter) throws Exception {
        final List<Customer> existingCustomers = Lists.newArrayListWithCapacity(10);

        dbi.inTransaction((conn, status) -> {
            Customers customers = conn.attach(Customers.class);

            try (ResultIterator<Customer> iterator = customers.get()) {
                while (iterator.hasNext()) {
                    existingCustomers.add(iterator.next());
                }
            }

            return null;
        });

        Commands.outputFormattedJson(objectMapper, entityWriter, queryParameters, existingCustomers);
    }
}
