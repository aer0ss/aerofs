package com.aerofs.webhooks.core.stripe.types.events;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import java.util.Map;

import com.aerofs.customerio.CustomerioClient;
import com.aerofs.customerio.CustomerioCustomer;
import com.aerofs.customerio.CustomerioEvent;
import com.aerofs.webhooks.core.stripe.ProcessContext;
import com.aerofs.webhooks.core.stripe.types.Customer;
import com.aerofs.webhooks.core.stripe.types.StripeEvent;
import com.aerofs.webhooks.entities.Organization;
import com.aerofs.webhooks.entities.OrganizationDao;
import com.aerofs.webhooks.entities.User;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

public abstract class CustomerEvent extends StripeEvent<Customer> {

    public CustomerEvent( final String id, final String eventType, final long created, final boolean livemode,
            final int pendingWebhooks,
            final EventData<Customer> data ) {
        super( id, eventType, created, livemode, pendingWebhooks, data );
    }

    public void processCustomer( final String eventId, final ProcessContext context ) {
        checkArgument( isNotBlank( eventId ), "eventId cannot be blank" );

        final Customer customer = getObject();
        checkNotNull( customer, "customer cannot be null" );

        final OrganizationDao organizationDao = context.getOrganizationDao();
        final CustomerioClient customerioClient = context.getCustomerioClient();

        final String stripeCustomerId = customer.getId();
        final Organization organization = organizationDao.findByStripeCustomerId( stripeCustomerId );

        for ( final User administrator : organization.getAdministrators() ) {
            final String email = administrator.getId();
            final String firstName = administrator.getFirstName();
            final String lastName = administrator.getLastName();
            final boolean delinquent = customer.isDelinquent().or( false );
            final String organizationName = organization.getName();
            final int organizationSize = organization.getMembers().size();

            final Map<String, Object> userAttributes =
                    ImmutableMap.<String, Object> of( "first_name", firstName,
                        "last_name", lastName,
                        "delinquent", delinquent,
                        "organization_name", organizationName,
                        "organization_size", organizationSize );

            customerioClient.identifyCustomer( email, new CustomerioCustomer( email, Optional.of( userAttributes ) ) );
            customerioClient.trackEvent( email, new CustomerioEvent<>( eventId, Optional.of( customer ) ) );
        }
    }

}
