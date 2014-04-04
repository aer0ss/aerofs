package com.aerofs.webhooks.core.stripe.types;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import com.aerofs.webhooks.core.stripe.ProcessContext;
import com.aerofs.webhooks.core.stripe.types.events.ChargeFailed;
import com.aerofs.webhooks.core.stripe.types.events.ChargeRefunded;
import com.aerofs.webhooks.core.stripe.types.events.ChargeSucceeded;
import com.aerofs.webhooks.core.stripe.types.events.CouponCreated;
import com.aerofs.webhooks.core.stripe.types.events.CouponDeleted;
import com.aerofs.webhooks.core.stripe.types.events.CustomerCreated;
import com.aerofs.webhooks.core.stripe.types.events.CustomerDeleted;
import com.aerofs.webhooks.core.stripe.types.events.CustomerUpdated;
import com.aerofs.webhooks.core.stripe.types.events.InvoiceCreated;
import com.aerofs.webhooks.core.stripe.types.events.InvoiceItemCreated;
import com.aerofs.webhooks.core.stripe.types.events.InvoiceItemDeleted;
import com.aerofs.webhooks.core.stripe.types.events.InvoiceItemUpdated;
import com.aerofs.webhooks.core.stripe.types.events.InvoicePaymentFailed;
import com.aerofs.webhooks.core.stripe.types.events.InvoicePaymentSucceeded;
import com.aerofs.webhooks.core.stripe.types.events.InvoiceUpdated;
import com.aerofs.webhooks.core.stripe.types.events.Ping;
import com.aerofs.webhooks.core.stripe.types.events.PlanCreated;
import com.aerofs.webhooks.core.stripe.types.events.PlanDeleted;
import com.aerofs.webhooks.core.stripe.types.events.PlanUpdated;
import com.aerofs.webhooks.core.stripe.types.events.SubscriptionCreated;
import com.aerofs.webhooks.core.stripe.types.events.SubscriptionDeleted;
import com.aerofs.webhooks.core.stripe.types.events.SubscriptionTrialWillEnd;
import com.aerofs.webhooks.core.stripe.types.events.SubscriptionUpdated;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.google.common.base.Objects;
import com.google.common.base.Optional;

@JsonTypeInfo( use = Id.NAME, property = StripeEvent.EVENT_TYPE_KEY )
@JsonSubTypes( { @Type( value = ChargeFailed.class, name = ChargeFailed.STRIPE_ID ),
        @Type( value = ChargeRefunded.class, name = ChargeRefunded.STRIPE_ID ),
        @Type( value = ChargeSucceeded.class, name = ChargeSucceeded.STRIPE_ID ),
        @Type( value = CouponCreated.class, name = CouponCreated.STRIPE_ID ),
        @Type( value = CouponDeleted.class, name = CouponDeleted.STRIPE_ID ),
        @Type( value = CustomerCreated.class, name = CustomerCreated.STRIPE_ID ),
        @Type( value = CustomerDeleted.class, name = CustomerDeleted.STRIPE_ID ),
        @Type( value = CustomerUpdated.class, name = CustomerUpdated.STRIPE_ID ),
        @Type( value = InvoiceCreated.class, name = InvoiceCreated.STRIPE_ID ),
        @Type( value = InvoiceItemCreated.class, name = InvoiceItemCreated.STRIPE_ID ),
        @Type( value = InvoiceItemDeleted.class, name = InvoiceItemDeleted.STRIPE_ID ),
        @Type( value = InvoiceItemUpdated.class, name = InvoiceItemUpdated.STRIPE_ID ),
        @Type( value = InvoicePaymentFailed.class, name = InvoicePaymentFailed.STRIPE_ID ),
        @Type( value = InvoicePaymentSucceeded.class, name = InvoicePaymentSucceeded.STRIPE_ID ),
        @Type( value = InvoiceUpdated.class, name = InvoiceUpdated.STRIPE_ID ),
        @Type( value = Ping.class, name = Ping.STRIPE_ID ),
        @Type( value = PlanCreated.class, name = PlanCreated.STRIPE_ID ),
        @Type( value = PlanDeleted.class, name = PlanDeleted.STRIPE_ID ),
        @Type( value = PlanUpdated.class, name = PlanUpdated.STRIPE_ID ),
        @Type( value = SubscriptionCreated.class, name = SubscriptionCreated.STRIPE_ID ),
        @Type( value = SubscriptionDeleted.class, name = SubscriptionDeleted.STRIPE_ID ),
        @Type( value = SubscriptionTrialWillEnd.class, name = SubscriptionTrialWillEnd.STRIPE_ID ),
        @Type( value = SubscriptionUpdated.class, name = SubscriptionUpdated.STRIPE_ID ) } )
public abstract class StripeEvent<T extends AbstractStripeApiObject> extends AbstractIdentifiedStripeApiObject {

    @JsonProperty( PENDINGWEBHOOKS_KEY ) private final int pendingWebhooks;
    @JsonProperty( DATA_KEY ) private final EventData<T> data;
    @JsonProperty( LIVEMODE_KEY ) private final boolean livemode;
    @JsonProperty( CREATED_KEY ) private final long created;
    @JsonProperty( EVENT_TYPE_KEY ) private final String eventType;

    protected StripeEvent( final String id, final String eventType, final long created, final boolean livemode,
            final int pendingWebhooks, final EventData<T> data ) {
        super( id, STRIPE_ID );
        this.eventType = checkNotNull( eventType, "eventType cannot be null" );
        this.pendingWebhooks = pendingWebhooks;
        this.data = data;
        this.livemode = livemode;
        this.created = created;
    }

    public int getPendingWebhooks() {
        return pendingWebhooks;
    }

    public T getObject() {
        return data.getObject();
    }

    public boolean isLivemode() {
        return livemode;
    }

    public abstract void process( final ProcessContext context );

    public long getCreated() {
        return created;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper( this )
                .omitNullValues()
                .add( ID_KEY, getId() )
                .add( CREATED_KEY, getCreated() )
                .add( LIVEMODE_KEY, isLivemode() )
                .add( PENDINGWEBHOOKS_KEY, getPendingWebhooks() )
                .add( DATA_KEY, data )
                .toString();
    }

    public static final String EVENT_TYPE_KEY = "type";
    protected static final String DATA_KEY = "data";
    protected static final String PENDINGWEBHOOKS_KEY = "pending_webhooks";
    public static final String STRIPE_ID = "event";

    @JsonInclude( Include.NON_NULL )
    @JsonIgnoreProperties( ignoreUnknown = true )
    @JsonAutoDetect( getterVisibility = Visibility.NONE,
            isGetterVisibility = Visibility.NONE,
            creatorVisibility = Visibility.NONE,
            fieldVisibility = Visibility.NONE,
            setterVisibility = Visibility.NONE )
    protected static class EventData<T extends AbstractStripeApiObject> {

        @JsonProperty( OBJECT_KEY ) private final T object;
        @JsonProperty( PREVIOUS_ATTRIBUTES_KEY ) private final Optional<Map<String, Object>> previousAttributes;

        @JsonCreator
        private EventData( @JsonProperty( OBJECT_KEY ) final T object,
                @JsonProperty( PREVIOUS_ATTRIBUTES_KEY ) final Optional<Map<String, Object>> previousAttributes ) {
            this.object = object;
            this.previousAttributes = previousAttributes;
        }

        public T getObject() {
            return object;
        }

        public Optional<Map<String, Object>> getPreviousAttributes() {
            return previousAttributes;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper( this )
                    .add( OBJECT_KEY, getObject() )
                    .add( PREVIOUS_ATTRIBUTES_KEY, getPreviousAttributes() )
                    .toString();
        }

        private static final String OBJECT_KEY = "object";
        private static final String PREVIOUS_ATTRIBUTES_KEY = "previous_attributes";

    }

}
