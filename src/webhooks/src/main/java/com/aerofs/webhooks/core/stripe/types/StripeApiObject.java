package com.aerofs.webhooks.core.stripe.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo( use = Id.NAME, property = StripeApiObject.TYPE_KEY )
@JsonSubTypes( { @Type( value = Card.class, name = Card.STRIPE_TYPE ),
        @Type( value = Charge.class, name = Charge.STRIPE_TYPE ),
        @Type( value = Coupon.class, name = Coupon.STRIPE_TYPE ),
        @Type( value = Customer.class, name = Customer.STRIPE_TYPE ),
        @Type( value = Discount.class, name = Discount.STRIPE_TYPE ),
        // Event.class excluded because it is abstract, it ends up not being a problem because within the
        // context of this service we StripeApiObject deserialization is only happening on event bodies, which
        // are not events themselves.
        @Type( value = Invoice.class, name = Invoice.STRIPE_TYPE ),
        @Type( value = InvoiceItem.class, name = InvoiceItem.STRIPE_TYPE ),
        @Type( value = LineItem.class, name = LineItem.STRIPE_TYPE ),
        @Type( value = Plan.class, name = Plan.STRIPE_TYPE ),
        @Type( value = Subscription.class, name = Subscription.STRIPE_TYPE ),
        @Type( value = Token.class, name = Token.STRIPE_TYPE ) } )
public interface StripeApiObject {

    public static final String TYPE_KEY = "object";

    String getType();

}
