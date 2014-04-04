package com.aerofs.webhooks.core.stripe.types;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class Token extends AbstractIdentifiedStripeApiObject {

    @JsonProperty( LIVEMODE_KEY ) private final boolean livemode;
    @JsonProperty( CREATED_KEY ) private final long created;
    @JsonProperty( USED_KEY ) private final boolean used;
    @JsonProperty( CARD_KEY ) private final Optional<Card> card;

    @JsonCreator
    private Token( @JsonProperty( ID_KEY ) final String id,
            @JsonProperty( TYPE_KEY ) final String type,
            @JsonProperty( LIVEMODE_KEY ) final boolean livemode,
            @JsonProperty( CREATED_KEY ) final long created,
            @JsonProperty( USED_KEY ) final boolean used,
            @JsonProperty( CARD_KEY ) final Optional<Card> card ) {
        super( id, STRIPE_TYPE );
        this.livemode = livemode;
        this.created = created;
        this.used = used;
        this.card = card;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper( this )
                .omitNullValues()
                .add( ID_KEY, getId() )
                .add( LIVEMODE_KEY, isLivemode() )
                .add( CREATED_KEY, getCreated() )
                .add( USED_KEY, isUsed() )
                .add( CARD_KEY, getCard() )
                .toString();
    }

    public boolean isLivemode() {
        return livemode;
    }

    public long getCreated() {
        return created;
    }

    public boolean isUsed() {
        return used;
    }

    public Optional<Card> getCard() {
        return card;
    }

    private static final String USED_KEY = "used";
    private static final String CARD_KEY = "card";
    public static final String STRIPE_TYPE = "token";

}
