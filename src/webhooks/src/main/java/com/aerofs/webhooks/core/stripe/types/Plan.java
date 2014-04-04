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
public class Plan extends AbstractIdentifiedStripeApiObject {

    @JsonProperty( CREATED_KEY ) private final long created;
    @JsonProperty( LIVEMODE_KEY ) private final boolean livemode;
    @JsonProperty( AMOUNT_KEY ) private final int amount;
    @JsonProperty( CURRENCY_KEY ) private final String currency;
    @JsonProperty( INTERVAL_KEY ) private final String interval;
    @JsonProperty( INTERVAL_COUNT_KEY ) private final int intervalCount;
    @JsonProperty( NAME_KEY ) private final String name;
    @JsonProperty( TRIAL_PERIOD_DAYS_KEY ) private final int trialPeriodDays;

    @JsonCreator
    private Plan( @JsonProperty( ID_KEY ) final String id,
            @JsonProperty( TYPE_KEY ) final String type,
            @JsonProperty( CREATED_KEY ) final long created,
            @JsonProperty( LIVEMODE_KEY ) final boolean livemode,
            @JsonProperty( AMOUNT_KEY ) final int amount,
            @JsonProperty( CURRENCY_KEY ) final String currency,
            @JsonProperty( INTERVAL_KEY ) final String interval,
            @JsonProperty( INTERVAL_COUNT_KEY ) final int intervalCount,
            @JsonProperty( NAME_KEY ) final String name,
            @JsonProperty( TRIAL_PERIOD_DAYS_KEY ) final int trialPeriodDays ) {
        super( id, STRIPE_TYPE );
        this.amount = amount;
        this.currency = currency;
        this.interval = interval;
        this.intervalCount = intervalCount;
        this.name = name;
        this.trialPeriodDays = trialPeriodDays;
        this.livemode = livemode;
        this.created = created;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper( this )
                .omitNullValues()
                .add( ID_KEY, getId() )
                .add( LIVEMODE_KEY, isLivemode() )
                .add( NAME_KEY, getName() )
                .add( CREATED_KEY, getCreated() )
                .add( TRIAL_PERIOD_DAYS_KEY, getTrialPeriodDays() )
                .add( AMOUNT_KEY, getAmount() )
                .add( CURRENCY_KEY, getCurrency() )
                .add( INTERVAL_KEY, getInterval() )
                .add( INTERVAL_COUNT_KEY, getIntervalCount() )
                .toString();
    }

    public int getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getInterval() {
        return interval;
    }

    public int getIntervalCount() {
        return intervalCount;
    }

    public String getName() {
        return name;
    }

    public int getTrialPeriodDays() {
        return trialPeriodDays;
    }

    public boolean isLivemode() {
        return livemode;
    }

    public long getCreated() {
        return created;
    }

    private static final String INTERVAL_KEY = "interval";
    private static final String NAME_KEY = "name";
    private static final String AMOUNT_KEY = "amount";
    private static final String CURRENCY_KEY = "currency";
    private static final String INTERVAL_COUNT_KEY = "interval_count";
    private static final String TRIAL_PERIOD_DAYS_KEY = "trial_period_days";
    public static final String STRIPE_TYPE = "plan";

}
