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
public class Customer extends AbstractIdentifiedStripeApiObject {

    @JsonProperty( LIVEMODE_KEY ) private final boolean livemode;
    @JsonProperty( CREATED_KEY ) private final long created;
    @JsonProperty( ACCOUNT_BALANCE_KEY ) private final Optional<Integer> accountBalance;
    @JsonProperty( ACTIVE_CARD_KEY ) private final Optional<Card> activeCard;
    @JsonProperty( DELINQUENT_KEY ) private final Optional<Boolean> delinquent;
    @JsonProperty( DESCRIPTION_KEY ) private final Optional<String> description;
    @JsonProperty( DISCOUNT_KEY ) private final Optional<Discount> discount;
    @JsonProperty( EMAIL_KEY ) private final Optional<String> email;
    @JsonProperty( SUBSCRIPTION_KEY ) private final Optional<Subscription> subscription;

    @JsonCreator
    private Customer( @JsonProperty( ID_KEY ) final String id,
            @JsonProperty( TYPE_KEY ) final String type,
            @JsonProperty( LIVEMODE_KEY ) final boolean livemode,
            @JsonProperty( CREATED_KEY ) final long created,
            @JsonProperty( ACCOUNT_BALANCE_KEY ) final Optional<Integer> accountBalance,
            @JsonProperty( ACTIVE_CARD_KEY ) final Optional<Card> activeCard,
            @JsonProperty( DELINQUENT_KEY ) final Optional<Boolean> delinquent,
            @JsonProperty( DESCRIPTION_KEY ) final Optional<String> description,
            @JsonProperty( DISCOUNT_KEY ) final Optional<Discount> discount,
            @JsonProperty( EMAIL_KEY ) final Optional<String> email,
            @JsonProperty( SUBSCRIPTION_KEY ) final Optional<Subscription> subscription ) {
        super( id, STRIPE_TYPE );
        this.livemode = livemode;
        this.created = created;
        this.accountBalance = accountBalance;
        this.activeCard = activeCard;
        this.delinquent = delinquent;
        this.description = description;
        this.discount = discount;
        this.email = email;
        this.subscription = subscription;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper( this )
                .omitNullValues()
                .add( ID_KEY, getId() )
                .add( CREATED_KEY, getCreated() )
                .add( LIVEMODE_KEY, isLivemode() )
                .add( EMAIL_KEY, getEmail() )
                .add( DESCRIPTION_KEY, getDescription() )
                .add( ACTIVE_CARD_KEY, getActiveCard() )
                .add( SUBSCRIPTION_KEY, getSubscription() )
                .add( DISCOUNT_KEY, getDiscount() )
                .add( ACCOUNT_BALANCE_KEY, getAccountBalance() )
                .add( DELINQUENT_KEY, isDelinquent() )
                .toString();
    }

    public boolean isLivemode() {
        return livemode;
    }

    public long getCreated() {
        return created;
    }

    public Optional<Integer> getAccountBalance() {
        return accountBalance;
    }

    public Optional<Card> getActiveCard() {
        return activeCard;
    }

    public Optional<Boolean> isDelinquent() {
        return delinquent;
    }

    public Optional<String> getDescription() {
        return description;
    }

    public Optional<Discount> getDiscount() {
        return discount;
    }

    public Optional<String> getEmail() {
        return email;
    }

    public Optional<Subscription> getSubscription() {
        return subscription;
    }

    private static final String LIVEMODE_KEY = "livemode";
    private static final String CREATED_KEY = "created";
    private static final String ACCOUNT_BALANCE_KEY = "account_balance";
    private static final String ACTIVE_CARD_KEY = "active_card";
    private static final String DELINQUENT_KEY = "delinquent";
    private static final String DESCRIPTION_KEY = "description";
    private static final String EMAIL_KEY = "email";
    private static final String SUBSCRIPTION_KEY = "subscription";
    private static final String DISCOUNT_KEY = "discount";
    public static final String STRIPE_TYPE = "customer";

}
