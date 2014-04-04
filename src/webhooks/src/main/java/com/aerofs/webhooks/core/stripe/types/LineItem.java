package com.aerofs.webhooks.core.stripe.types;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Optional;

@JsonInclude( Include.NON_NULL )
@JsonIgnoreProperties( ignoreUnknown = true )
@JsonAutoDetect( getterVisibility = Visibility.NONE,
        isGetterVisibility = Visibility.NONE,
        creatorVisibility = Visibility.NONE,
        fieldVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE )
public class LineItem extends InvoiceItem {

    @JsonCreator
    protected LineItem( @JsonProperty( ID_KEY ) final String id,
            @JsonProperty( TYPE_KEY ) final String type,
            @JsonProperty( LIVEMODE_KEY ) final boolean livemode,
            @JsonProperty( AMOUNT_KEY ) final int amount,
            @JsonProperty( CURRENCY_KEY ) final String currency,
            @JsonProperty( PERIOD_KEY ) final Period period,
            @JsonProperty( PRORATION_KEY ) final boolean proration,
            @JsonProperty( ITEM_TYPE_KEY ) final String itemType,
            @JsonProperty( DESCRIPTION_KEY ) final Optional<String> description,
            @JsonProperty( PLAN_KEY ) final Optional<Plan> plan,
            @JsonProperty( QUANTITY_KEY ) final Optional<Integer> quantity ) {
        super( id, type, livemode, amount, currency, period, proration, itemType, description, plan, quantity );
    }

    public static final String STRIPE_TYPE = "line_item";

}
