package com.aerofs.webhooks.core.stripe.types.events;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerofs.customerio.CustomerioClient;
import com.aerofs.customerio.CustomerioCustomer;
import com.aerofs.customerio.CustomerioEvent;
import com.aerofs.webhooks.core.stripe.ProcessContext;
import com.aerofs.webhooks.core.stripe.types.Charge;
import com.aerofs.webhooks.core.stripe.types.StripeEvent;
import com.aerofs.webhooks.entities.Organization;
import com.aerofs.webhooks.entities.OrganizationDao;
import com.aerofs.webhooks.entities.User;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

public abstract class ChargeEvent extends StripeEvent<Charge> {

    public ChargeEvent( final String id, final String eventType, final long created, final boolean livemode,
            final int pendingWebhooks, final EventData<Charge> data ) {
        super( id, eventType, created, livemode, pendingWebhooks, data );
    }

    public void processCharge( final String eventId, final ProcessContext context ) {
        checkArgument( isNotBlank( eventId ), "eventId cannot be blank" );

        final Charge charge = getObject();
        checkNotNull( charge, "charge cannot be null" );

        final OrganizationDao organizationDao = context.getOrganizationDao();
        final CustomerioClient customerioClient = context.getCustomerioClient();

        final Optional<String> stripeCustomerId = charge.getCustomerId();

        if ( !stripeCustomerId.isPresent() ) {
            // if there is no customer, kind of hard to email no one
            LOGGER.warn( "Returning early because charge has no customer, charge: {}", charge );
            return;
        }

        final Organization organization = organizationDao.findByStripeCustomerId( stripeCustomerId.get() );

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
            customerioClient.trackEvent( email, new CustomerioEvent<>( eventId, Optional.of( charge ) ) );
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger( ChargeRefunded.class );

}
