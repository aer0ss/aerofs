package com.aerofs.baseline.simple.resources;

import com.aerofs.baseline.auth.AeroPrincipal;
import com.aerofs.baseline.simple.SimpleConfiguration;
import com.aerofs.baseline.simple.api.Customer;
import com.aerofs.baseline.simple.db.Customers;
import com.google.common.base.Preconditions;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.Min;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

/**
 * Resource for interacting with a specific customer.
 * <p/>
 * Supports the following routes:
 * <ul>
 *     <li>{@code POST /customers/[customer_id]}</li>
 *     <li>{@code GET  /customers/[customer_id]}</li>
 * </ul>
 */
@PermitAll
public final class CustomerResource {

    private final DBI dbi;
    private final SimpleConfiguration configuration;

    public CustomerResource(@Context DBI dbi, @Context SimpleConfiguration configuration) {
        this.dbi = dbi;
        this.configuration = configuration;
    }

    /**
     * Update the number of seats a customer has.
     * <br/>
     * Can only be accessed by valid AeroFS clients.
     */
    @RolesAllowed(AeroPrincipal.Roles.CLIENT)
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Customer updateSeats(@PathParam("customer_id") @Min(1) final int customerId, @QueryParam("seats") @Min(1) final int seats) {
        Preconditions.checkArgument(seats <= configuration.getMaxSeats(), "%s exceeds max seats (%s)", seats, configuration.getMaxSeats());

        return dbi.inTransaction(new TransactionCallback<Customer>() {

            @Override
            public Customer inTransaction(Handle conn, TransactionStatus status) throws Exception {
                Customers customers = conn.attach(Customers.class);

                if (customers.exists(customerId) == 0) {
                    throw new InvalidCustomerException(customerId);
                }

                customers.update(customerId, seats);

                return customers.get(customerId);
            }
        });
    }

    /**
     * Get information for a customer identified by {@code customerId}.
     * <br/>
     * Can only be accessed by any client.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Customer get(@PathParam("customer_id") @Min(1) final int customerId) {
        return dbi.inTransaction(new TransactionCallback<Customer>() {

            @Override
            public Customer inTransaction(Handle conn, TransactionStatus status) throws Exception {
                Customers customers = conn.attach(Customers.class);

                Customer customer = customers.get(customerId);
                if (customer == null) {
                    throw new InvalidCustomerException(customerId);
                }

                return customer;
            }
        });
    }
}
