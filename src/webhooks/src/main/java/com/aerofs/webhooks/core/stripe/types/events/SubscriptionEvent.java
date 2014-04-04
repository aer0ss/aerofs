package com.aerofs.webhooks.core.stripe.types.events;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import com.aerofs.customerio.CustomerioClient;
import com.aerofs.customerio.CustomerioCustomer;
import com.aerofs.customerio.CustomerioEvent;
import com.aerofs.webhooks.core.stripe.ProcessContext;
import com.aerofs.webhooks.core.stripe.types.StripeEvent;
import com.aerofs.webhooks.core.stripe.types.Subscription;
import com.aerofs.webhooks.entities.Organization;
import com.aerofs.webhooks.entities.OrganizationDao;
import com.aerofs.webhooks.entities.User;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

public abstract class SubscriptionEvent extends StripeEvent<Subscription> {

    public SubscriptionEvent( final String id, final String eventType, final long created, final boolean livemode,
            final int pendingWebhooks,
            final EventData<Subscription> data ) {
        super( id, eventType, created, livemode, pendingWebhooks, data );
    }

    public void processSubscription( String eventId, ProcessContext context ) {
        final Subscription subscription = getObject();
        checkNotNull( getObject(), "subscription cannot be null" );

        final OrganizationDao organizationDao = context.getOrganizationDao();
        final CustomerioClient customerioClient = context.getCustomerioClient();

        final String stripeCustomerId = subscription.getCustomerId();
        final Organization organization = organizationDao.findByStripeCustomerId( stripeCustomerId );

        for ( final User administrator : organization.getAdministrators() ) {
            final String email = administrator.getId();
            final String firstName = administrator.getFirstName();
            final String lastName = administrator.getLastName();
            final String organizationName = organization.getName();
            final int organizationSize = organization.getMembers().size();

            final Map<String, Object> userAttributes =
                    ImmutableMap.<String, Object> of( "first_name", firstName,
                        "last_name", lastName,
                        "organization_name", organizationName,
                        "organization_size", organizationSize );

            customerioClient.identifyCustomer( email, new CustomerioCustomer( email, Optional.of( userAttributes ) ) );
            customerioClient.trackEvent( email, new CustomerioEvent<>( eventId, Optional.of( subscription ) ) );
        }
    }

}
