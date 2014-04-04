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
public class Subscription extends AbstractStripeApiObject {

    @JsonProperty( CANCEL_AT_PERIOD_END_KEY ) private final boolean cancelAtPeriodEnd;
    @JsonProperty( CUSTOMER_KEY ) private final String customerId;
    @JsonProperty( PLAN_KEY ) private final Plan plan;
    @JsonProperty( QUANTITY_KEY ) private final int quantity;
    @JsonProperty( START_KEY ) private final long start;
    @JsonProperty( STATUS_KEY ) private final String status;
    @JsonProperty( CANCELED_AT_KEY ) private final Optional<Long> canceledAt;
    @JsonProperty( CURRENT_PERIOD_END_KEY ) private final Optional<Long> currentPeriodEnd;
    @JsonProperty( CURRENT_PERIOD_START_KEY ) private final Optional<Long> currentPeriodStart;
    @JsonProperty( ENDED_AT_KEY ) private final Optional<Long> endedAt;
    @JsonProperty( TRIAL_END_KEY ) private final Optional<Long> trialEnd;
    @JsonProperty( TRIAL_START_KEY ) private final Optional<Long> trialStart;

    @JsonCreator
    private Subscription( @JsonProperty( TYPE_KEY ) final String type,
            @JsonProperty( CANCEL_AT_PERIOD_END_KEY ) final boolean cancelAtPeriodEnd,
            @JsonProperty( CUSTOMER_KEY ) final String customerId,
            @JsonProperty( PLAN_KEY ) final Plan plan,
            @JsonProperty( QUANTITY_KEY ) final int quantity,
            @JsonProperty( START_KEY ) final long start,
            @JsonProperty( STATUS_KEY ) final String status,
            @JsonProperty( CANCELED_AT_KEY ) final Optional<Long> canceledAt,
            @JsonProperty( CURRENT_PERIOD_END_KEY ) final Optional<Long> currentPeriodEnd,
            @JsonProperty( CURRENT_PERIOD_START_KEY ) final Optional<Long> currentPeriodStart,
            @JsonProperty( ENDED_AT_KEY ) final Optional<Long> endedAt,
            @JsonProperty( TRIAL_END_KEY ) final Optional<Long> trialEnd,
            @JsonProperty( TRIAL_START_KEY ) final Optional<Long> trialStart ) {
        super( STRIPE_TYPE );
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
        this.customerId = customerId;
        this.plan = plan;
        this.quantity = quantity;
        this.start = start;
        this.status = status;
        this.canceledAt = canceledAt;
        this.currentPeriodEnd = currentPeriodEnd;
        this.currentPeriodStart = currentPeriodStart;
        this.endedAt = endedAt;
        this.trialEnd = trialEnd;
        this.trialStart = trialStart;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper( this )
                .omitNullValues()
                .add( CUSTOMER_KEY, getCustomerId() )
                .add( CURRENT_PERIOD_START_KEY, getCurrentPeriodStart() )
                .add( CURRENT_PERIOD_END_KEY, getCurrentPeriodEnd() )
                .add( PLAN_KEY, getPlan() )
                .add( QUANTITY_KEY, getQuantity() )
                .add( START_KEY, getStart() )
                .add( STATUS_KEY, getStatus() )
                .add( CANCEL_AT_PERIOD_END_KEY, isCancelAtPeriodEnd() )
                .add( CANCELED_AT_KEY, getCanceledAt() )
                .add( ENDED_AT_KEY, getEndedAt() )
                .add( TRIAL_START_KEY, getTrialStart() )
                .add( TRIAL_END_KEY, getTrialEnd() )
                .toString();
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public String getCustomerId() {
        return customerId;
    }

    public Plan getPlan() {
        return plan;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getStart() {
        return start;
    }

    public String getStatus() {
        return status;
    }

    public Optional<Long> getCanceledAt() {
        return canceledAt;
    }

    public Optional<Long> getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public Optional<Long> getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public Optional<Long> getEndedAt() {
        return endedAt;
    }

    public Optional<Long> getTrialEnd() {
        return trialEnd;
    }

    public Optional<Long> getTrialStart() {
        return trialStart;
    }

    public static final String CANCEL_AT_PERIOD_END_KEY = "cancel_at_period_end";
    public static final String CUSTOMER_KEY = "customer";
    public static final String PLAN_KEY = "plan";
    public static final String QUANTITY_KEY = "quantity";
    public static final String START_KEY = "start";
    public static final String STATUS_KEY = "status";
    public static final String CANCELED_AT_KEY = "canceled_at";
    public static final String CURRENT_PERIOD_END_KEY = "current_period_end";
    public static final String CURRENT_PERIOD_START_KEY = "current_period_start";
    public static final String ENDED_AT_KEY = "ended_at";
    public static final String TRIAL_END_KEY = "trial_end";
    public static final String TRIAL_START_KEY = "trial_start";
    public static final String STRIPE_TYPE = "subscription";

}
