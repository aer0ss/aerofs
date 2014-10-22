package com.aerofs.baseline.simple.resources;

import com.aerofs.baseline.auth.AeroPrincipal;
import com.aerofs.baseline.metrics.MetricRegistries;
import com.aerofs.baseline.simple.SimpleConfiguration;
import com.aerofs.baseline.simple.api.Customer;
import com.aerofs.baseline.simple.db.Customers;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.base.Preconditions;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

/**
 * Resource with which clients can add customers to the database.
 * <p/>
 * Supports the following routes:
 * <ul>
 *     <li>{@code POST /customers/}</li>
 * </ul>
 * This resource can only be accessed by valid AeroFS clients.
 */
@Path("/customers")
public final class CustomersResource {

    private static final Timer CUSTOMER_CREATION_TIMER = MetricRegistries.getRegistry().timer(MetricRegistry.name("simple", "customer", "new"));

    private final DBI dbi;
    private final SimpleConfiguration configuration;
    private final ResourceContext resourceContext;

    public CustomersResource(@Context DBI dbi, @Context SimpleConfiguration configuration, @Context ResourceContext resourceContext) {
        this.dbi = dbi;
        this.configuration = configuration;
        this.resourceContext = resourceContext;
    }

    /**
     * Add a customer.
     * @return the customer id of the newly-added customer
     */
    @RolesAllowed(AeroPrincipal.Roles.CLIENT)
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public int add(final Customer customer) {
        Timer.Context timerContext = CUSTOMER_CREATION_TIMER.time();

        try {
            Preconditions.checkArgument(customer.seats <= configuration.getMaxSeats(), "%s exceeds max seats (%s)", customer.seats, configuration.getMaxSeats());

            return dbi.inTransaction(new TransactionCallback<Integer>() {

                @Override
                public Integer inTransaction(Handle conn, TransactionStatus status) throws Exception {
                    Customers customers = conn.attach(Customers.class);

                    return customers.add(customer.customerName, customer.organizationName, customer.seats);
                }
            });
        } finally {
            timerContext.stop();
        }
    }

    // this is a JAX-RS sub-resource
    @Path("/{customer_id}")
    public CustomerResource getCustomer() {
        return resourceContext.getResource(CustomerResource.class);
    }
}