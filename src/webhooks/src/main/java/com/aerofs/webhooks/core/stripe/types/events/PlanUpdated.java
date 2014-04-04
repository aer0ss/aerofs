package com.aerofs.webhooks.core.stripe.types.events;

import com.aerofs.webhooks.core.stripe.ProcessContext;
import com.aerofs.webhooks.core.stripe.types.StripeEvent;
import com.aerofs.webhooks.core.stripe.types.Plan;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonIgnoreProperties( ignoreUnknown = true )
@JsonInclude( Include.NON_NULL )
@JsonAutoDetect( getterVisibility = Visibility.NONE,
        isGetterVisibility = Visibility.NONE,
        creatorVisibility = Visibility.NONE,
        fieldVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE )
public class PlanUpdated extends StripeEvent<Plan> {

    @JsonCreator
    private PlanUpdated( @JsonProperty( ID_KEY ) final String id,
            @JsonProperty( TYPE_KEY ) final String type,
            @JsonProperty( EVENT_TYPE_KEY ) final String eventType,
            @JsonProperty( CREATED_KEY ) final long created,
            @JsonProperty( LIVEMODE_KEY ) final boolean livemode,
            @JsonProperty( PENDINGWEBHOOKS_KEY ) final int pendingWebhooks,
            @JsonProperty( DATA_KEY ) final EventData<Plan> data ) {
        super( id, STRIPE_ID, created, livemode, pendingWebhooks, data );
    }

    @Override
    public void process( final ProcessContext context ) {
    }

    public static final String STRIPE_ID = "plan.updated";

}
