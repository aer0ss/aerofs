package com.aerofs.webhooks.core.stripe.types;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.base.Objects;

@JsonInclude( Include.NON_NULL )
@JsonIgnoreProperties( ignoreUnknown = true )
@JsonAutoDetect( getterVisibility = Visibility.NONE,
        isGetterVisibility = Visibility.NONE,
        creatorVisibility = Visibility.NONE,
        fieldVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE )
public class Discount extends AbstractStripeApiObject {

    @JsonProperty( CUSTOMER_KEY ) private final String customerId;
    @JsonProperty( START_KEY ) private final long start;
    @JsonProperty( END_KEY ) private final long end;
    @JsonProperty( COUPON_KEY ) private final Coupon coupon;
    @JsonProperty( TYPE_KEY ) private final String type = STRIPE_TYPE;

    @JsonCreator
    private Discount( @JsonProperty( TYPE_KEY ) final String type,
            @JsonProperty( CUSTOMER_KEY ) final String customerId,
            @JsonProperty( START_KEY ) final long start,
            @JsonProperty( END_KEY ) final long end,
            @JsonProperty( COUPON_KEY ) final Coupon coupon ) {
        super( STRIPE_TYPE );
        this.end = end;
        this.start = start;
        this.coupon = coupon;
        this.customerId = customerId;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper( this )
                .omitNullValues()
                .add( CUSTOMER_KEY, getCustomerId() )
                .add( START_KEY, getStart() )
                .add( END_KEY, getEnd() )
                .add( COUPON_KEY, getCoupon() )
                .toString();
    }

    public long getEnd() {
        return end;
    }

    public long getStart() {
        return start;
    }

    public Coupon getCoupon() {
        return coupon;
    }

    public String getCustomerId() {
        return customerId;
    }

    private static final String CUSTOMER_KEY = "customer";
    private static final String START_KEY = "start";
    private static final String END_KEY = "end";
    private static final String COUPON_KEY = "coupon";
    public static final String STRIPE_TYPE = "discount";

}
