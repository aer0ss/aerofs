package com.aerofs.webhooks.core.stripe.types;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.base.Objects;
import com.google.common.base.Optional;

@JsonInclude( Include.NON_NULL )
@JsonIgnoreProperties( ignoreUnknown = true )
@JsonAutoDetect( getterVisibility = Visibility.NONE,
        isGetterVisibility = Visibility.NONE,
        creatorVisibility = Visibility.NONE,
        fieldVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE )
public class Coupon extends AbstractIdentifiedStripeApiObject {

    @JsonProperty( PERCENT_OFF_KEY ) private final Optional<Integer> percentOff;
    @JsonProperty( AMOUNT_OFF_KEY ) private final Optional<Integer> amountOff;
    @JsonProperty( CURRENCY_KEY ) private final Optional<String> currency;
    @JsonProperty( DURATION_KEY ) private final String duration;
    @JsonProperty( LIVEMODE_KEY ) private final boolean livemode;
    @JsonProperty( DURATION_IN_MONTHS_KEY ) private final Optional<Integer> durationInMonths;
    @JsonProperty( MAX_REDEMPTIONS_KEY ) private final Optional<Integer> maxRedemptions;
    @JsonProperty( REDEEM_BY_KEY ) private final Optional<Long> redeemBy;
    @JsonProperty( TIMES_REDEEMED_KEY ) private final Optional<Integer> timesRedeemed;

    @JsonCreator
    private Coupon( @JsonProperty( ID_KEY ) final String id,
            @JsonProperty( TYPE_KEY ) final String type,
            @JsonProperty( PERCENT_OFF_KEY ) final Optional<Integer> percentOff,
            @JsonProperty( AMOUNT_OFF_KEY ) final Optional<Integer> amountOff,
            @JsonProperty( CURRENCY_KEY ) final Optional<String> currency,
            @JsonProperty( DURATION_KEY ) final String duration,
            @JsonProperty( LIVEMODE_KEY ) final boolean livemode,
            @JsonProperty( DURATION_IN_MONTHS_KEY ) final Optional<Integer> durationInMonths,
            @JsonProperty( MAX_REDEMPTIONS_KEY ) final Optional<Integer> maxRedemptions,
            @JsonProperty( REDEEM_BY_KEY ) final Optional<Long> redeemBy,
            @JsonProperty( TIMES_REDEEMED_KEY ) final Optional<Integer> timesRedeemed ) {
        super( id, STRIPE_TYPE );
        this.percentOff = percentOff;
        this.amountOff = amountOff;
        this.currency = currency;
        this.duration = duration;
        this.livemode = livemode;
        this.durationInMonths = durationInMonths;
        this.maxRedemptions = maxRedemptions;
        this.redeemBy = redeemBy;
        this.timesRedeemed = timesRedeemed;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper( this )
                .omitNullValues()
                .add( ID_KEY, getId() )
                .add( LIVEMODE_KEY, isLivemode() )
                .add( PERCENT_OFF_KEY, getPercentOff() )
                .add( AMOUNT_OFF_KEY, getAmountOff() )
                .add( CURRENCY_KEY, getCurrency() )
                .add( DURATION_KEY, getDuration() )
                .add( DURATION_IN_MONTHS_KEY, getDurationInMonths() )
                .add( REDEEM_BY_KEY, getRedeemBy() )
                .add( TIMES_REDEEMED_KEY, getTimesRedeemed() )
                .add( MAX_REDEMPTIONS_KEY, getMaxRedemptions() )
                .toString();
    }

    public Optional<Integer> getPercentOff() {
        return percentOff;
    }

    public Optional<Integer> getAmountOff() {
        return amountOff;
    }

    public String getDuration() {
        return duration;
    }

    public boolean isLivemode() {
        return livemode;
    }

    public Optional<Integer> getDurationInMonths() {
        return durationInMonths;
    }

    public Optional<Integer> getMaxRedemptions() {
        return maxRedemptions;
    }

    public Optional<Long> getRedeemBy() {
        return redeemBy;
    }

    public Optional<Integer> getTimesRedeemed() {
        return timesRedeemed;
    }

    public Optional<String> getCurrency() {
        return currency;
    }

    private static final String PERCENT_OFF_KEY = "percent_off";
    private static final String AMOUNT_OFF_KEY = "amount_off";
    private static final String CURRENCY_KEY = "currency";
    private static final String DURATION_KEY = "duration";
    private static final String REDEEM_BY_KEY = "redeem_by";
    private static final String MAX_REDEMPTIONS_KEY = "max_redemptions";
    private static final String TIMES_REDEEMED_KEY = "times_redeemed";
    private static final String DURATION_IN_MONTHS_KEY = "duration_in_months";
    public static final String STRIPE_TYPE = "coupon";

}
